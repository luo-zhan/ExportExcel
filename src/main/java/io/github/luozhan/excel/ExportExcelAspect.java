package io.github.luozhan.excel;

import io.github.luozhan.excel.paramhandle.req.PageParamHandler;
import io.github.luozhan.excel.paramhandle.req.PageParamProxy;
import io.github.luozhan.excel.style.AdaptiveWidthStyleStrategy;
import org.apache.commons.codec.CharEncoding;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.write.builder.ExcelWriterBuilder;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

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
    private static final String FILE_NAME_KEYWORDS = "fileName";

    @Resource
    private ConversionService mvcConversionService;

    @Resource
    private ObjectProvider<PageParamHandler<?>> pageParamHandlers;

    @Resource
    private ObjectProvider<Converter<?>> converterProvider;

    // 关键：注入所有的ResponseBodyAdvice，Spring会自动按@Order排序
    @Resource
    private ObjectProvider<ResponseBodyAdvice<?>> responseBodyAdvices;

    // 注入消息转换器，用于模拟Spring MVC的MediaType选择逻辑
    @Resource
    private HttpMessageConverters messageConverters;

    @Around("@annotation(exportExcel)")
    public Object around(ProceedingJoinPoint point, ExportExcel exportExcel) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes());
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = Objects.requireNonNull(attributes.getResponse());
        if (!isExportRequest(request)) {
            // 非导出请求，放行
            return point.proceed();
        }
        MethodSignature methodSignature = (MethodSignature) point.getSignature();
        Method method = methodSignature.getMethod();
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
        // 设置响应头
        setExcelResponse(response, fileName);

        // 提前构造MethodParameter，所有批次复用
        MethodParameter returnType = new MethodParameter(method, -1);

        if (batchSize > 0 && pageParamProxy != null) {
            // 有分页参数，分批查询然后写入excel
            batchWrite(point, pageParamProxy, batchSize, maxSize, voClass, sheetName, request, response, returnType);
        } else {
            // 无分页参数，全量查询，一次性写入

            Object result = point.proceed();
            // 执行所有ResponseBodyAdvice，得到转换后的数据
            result = applyResponseBodyAdvices(result, returnType, request, response);
            List<?> data = extractData(result);
            log.info("全量查询数据一次性写入excel，数据量：{}条", data.size());
            checkMaxSize(data.size(), maxSize);
            ExcelWriterBuilder excelWriterBuilder = FesodSheet.write(response.getOutputStream(), voClass)
                    .registerWriteHandler(new AdaptiveWidthStyleStrategy());
            // 注入用户自定义数据转换器
            converterProvider.stream().forEach(excelWriterBuilder::registerConverter);
            excelWriterBuilder.sheet(sheetName).doWrite(data);
        }

        return null;
    }

    /**
     * 核心方法：手动执行所有匹配的ResponseBodyAdvice
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object applyResponseBodyAdvices(Object body, MethodParameter returnType, HttpServletRequest request, HttpServletResponse response) {
        ServerHttpRequest serverRequest = new ServletServerHttpRequest(request);
        ServerHttpResponse serverResponse = new ServletServerHttpResponse(response);
        // 按Spring注入的顺序（@Order从小到大）依次执行
        for (ResponseBodyAdvice advice : responseBodyAdvices) {
            if (advice.supports(returnType, MappingJackson2HttpMessageConverter.class)) {
                body = advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class, serverRequest, serverResponse);
            }
        }
        return body;
    }


    /**
     * 分批写excel
     */
    private void batchWrite(ProceedingJoinPoint point,
                            PageParamProxy<?> pageParamProxy,
                            int batchSize, long maxSize,
                            Class<?> voClass,
                            String sheetName,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            MethodParameter returnType) throws Throwable {
        ExcelWriterBuilder excelWriterBuilder = FesodSheet.write(response.getOutputStream(), voClass)
                .registerWriteHandler(new AdaptiveWidthStyleStrategy());
        // 注入用户自定义数据转换器
        converterProvider.orderedStream().forEach(excelWriterBuilder::registerConverter);
        try (ExcelWriter excelWriter = excelWriterBuilder.build()) {
            WriteSheet writeSheet = FesodSheet.writerSheet(sheetName).build();

            int pageNum = 1;
            long totalCount = 0;
            try {
                while (true) {
                    pageParamProxy.setPageParam(pageNum, batchSize);
                    Object result = point.proceed();
                    // 执行所有ResponseBodyAdvice
                    result = applyResponseBodyAdvices(result, returnType, request, response);
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
                log.warn("导出数据时发生异常", e);
                // 文件已经开始下载，没法撤回，只能在excel中提示错误信息
                excelWriter.write(Collections.singleton(Collections.singleton("⚠️" + e.getMessage() + "，已终止下载，该数据不完整！")), writeSheet);
            }
        }
    }

    // 以下方法和原AOP完全一致，无需修改
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
        if (args.length != 0) {
            log.info("注意，未找到分页入参，将查询全量数据");
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
        if (mvcConversionService != null && mvcConversionService.canConvert(resultClass, List.class)) {
            List<?> convert = mvcConversionService.convert(result, List.class);
            if (convert != null && !convert.isEmpty() && convert.get(0).getClass().equals(resultClass)) {
                // 走的是spring官方的ObjectToCollectionConvert，没有自定义的转换器，报错
                throw new IllegalArgumentException("不支持的返回类型: " + resultClass.getName() + "，请自定义转换器实现ExcelDataConverter接口，并注入spring容器");
            }
            return convert;
        }
        throw new IllegalArgumentException("不支持的返回类型: " + resultClass.getName() + "，请自定义转换器实现ExcelDataConverter接口，并注入spring容器");
    }

    private boolean isExportRequest(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(ExportHandlerMapping.EXPORT_FLAG_ATTRIBUTE));
    }
}
