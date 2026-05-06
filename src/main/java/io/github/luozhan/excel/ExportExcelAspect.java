package io.github.luozhan.excel;

import io.github.luozhan.excel.page.PageParamHandler;
import io.github.luozhan.excel.page.PageParamProxy;
import io.github.luozhan.excel.style.AdaptiveWidthStyleStrategy;
import org.apache.commons.codec.CharEncoding;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.NonNull;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Excel导出AOP
 *
 * @author luozhan
 * @since 2026-05-04
 */
@Aspect
@Order(1)
public class ExportExcelAspect {
    private static final Logger log = LoggerFactory.getLogger(ExportExcelAspect.class);
    private static final String EXPORT_KEYWORDS = "export";
    private static final String FILE_NAME_KEYWORDS = "fileName";

    @Resource
    private ConversionService conversionService;

    @Resource
    private List<PageParamHandler<?>> pageParamHandlers;

    @Around("@annotation(io.github.luozhan.excel.ExportExcel)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes());
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = Objects.requireNonNull(attributes.getResponse());
        if (!isExportRequest(request)) {
            // 非导出请求，放行
            return point.proceed();
        }
        MethodSignature methodSignature = (MethodSignature) point.getSignature();
        Method method = methodSignature.getMethod();
        ExportExcel exportExcel = method.getAnnotation(ExportExcel.class);
        // 文件名，优先请求参数，其次使用注解配置
        String fileName = request.getParameter(FILE_NAME_KEYWORDS);
        fileName = URLEncoder.encode(fileName != null ? fileName : exportExcel.fileName(), StandardCharsets.UTF_8.name());
        String sheetName = exportExcel.sheetName();

        // 解析VO类型
        Class<?> voClass = getActualVoType(method);
        int batchSize = exportExcel.batchSize();
        long maxSize = exportExcel.limit();
        // 获取分页参数
        PageParamProxy<?> pageParamProxy = findPageableParam(point.getArgs());
        // 设置请求头
        setExcelResponse(response, fileName);

        if (batchSize > 0 && pageParamProxy != null) {
            // 有分页参数，分批查询然后写入excel
            writeInBatches(point, pageParamProxy, batchSize, maxSize, voClass, sheetName, response);
        } else {
            // 无分页参数，全量查询，注意查询方法要控制数据量，太大则改成分页查询
            Object result = point.proceed();
            List<?> data = extractData(result);
            checkMaxSize(data.size(), maxSize);
            FesodSheet.write(response.getOutputStream(), voClass).registerWriteHandler(new AdaptiveWidthStyleStrategy()).sheet(sheetName).doWrite(data);
        }

        return null;
    }

    /**
     * 获取方法返回VO类
     */
    private static Class<?> getActualVoType(Method method) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        // 递归解析：一直取第一个泛型参数，直到拿到最终的非泛型Class
        while (returnType.hasGenerics()) {
            returnType = returnType.getGeneric(0);
        }
        //  最终的实际类型
        Class<?> voClass = returnType.resolve();
        if (voClass == null) {
            throw new IllegalArgumentException("方法【" + method.getName() + "】返回类型解析失败，请使用具体泛型");
        }
        return voClass;
    }

    private static void setExcelResponse(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(CharEncoding.UTF_8);
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
    }

    /**
     * 分批写excel
     */
    private void writeInBatches(ProceedingJoinPoint point, PageParamProxy<?> pageParamProxy, int batchSize, long maxSize, Class<?> voClass, String sheetName, HttpServletResponse response) throws Throwable {
        try (ExcelWriter excelWriter = FesodSheet.write(response.getOutputStream(), voClass)
                .registerWriteHandler(new AdaptiveWidthStyleStrategy())
                .build()) {

            WriteSheet writeSheet = FesodSheet.writerSheet(sheetName).build();

            int pageNum = 1;
            long totalCount = 0;
            try {
                while (true) {
                    pageParamProxy.setPageParam(pageNum, batchSize);
                    Object result;
                    result = point.proceed();

                    List<?> data = extractData(result);

                    if (data.isEmpty()) {
                        break;
                    }
                    totalCount += data.size();
                    // 检查是否超出最大限制
                    checkMaxSize(totalCount, maxSize);

                    // 写入excel
                    excelWriter.write(data, writeSheet);

                    if (data.size() < batchSize) {
                        // 数据查询完毕
                        break;
                    }
                    pageNum++;
                }
            } catch (Exception e) {
                // 文件已经开始下载，没法撤回，只能在excel中提示错误信息
                excelWriter.write(Collections.singleton(Collections.singleton("⚠️" + e.getMessage() + "，已终止下载，该数据不完整！")), writeSheet);
            }
        }
    }

    private void checkMaxSize(long currentSize, long maxSize) {
        if (maxSize > 0 && currentSize > maxSize) {
            throw new IllegalArgumentException("导出数据量超过限制，当前" + currentSize + "条");
        }
    }

    /**
     * 解析分页类参数
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private PageParamProxy<?> findPageableParam(@NonNull Object[] args) {
        if (pageParamHandlers == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            for (PageParamHandler<?> pageParamHandler : pageParamHandlers) {
                // 判断是否继承了声明的Page类
                if (pageParamHandler.getSupportType().isAssignableFrom(arg.getClass())) {
                    log.info("根据参数{}找到处理器{}", arg.getClass().getSimpleName(), pageParamHandler.getClass().getSimpleName());
                    return new PageParamProxy(arg, pageParamHandler);
                }
            }
        }
        return null;
    }

    /**
     * 提取数据
     */
    private List<?> extractData(Object result) {
        if (result instanceof List) {
            return (List<?>) result;
        }
        Class<?> resultClass = result.getClass();
        if (conversionService != null && conversionService.canConvert(resultClass, List.class)) {
            return conversionService.convert(result, List.class);
        }
        throw new IllegalArgumentException("不支持的返回类型: " + resultClass.getName() + "，请自定义转换器实现PageToListConverter接口，并注入spring容器");
    }

    private boolean isExportRequest(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(ExportExcelFilter.EXPORT_FLAG_ATTRIBUTE));
    }

}
