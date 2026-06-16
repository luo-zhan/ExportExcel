package io.github.luozhan.excel.core;

import io.github.luozhan.excel.core.ExcelContext.CursorState;
import io.github.luozhan.excel.cursor.CursorMetadata;
import io.github.luozhan.excel.cursor.CursorMetadataResolver;
import io.github.luozhan.excel.cursor.CursorPaginationInterceptor;
import io.github.luozhan.excel.paramhandle.req.PageParamHandler;
import io.github.luozhan.excel.paramhandle.req.PageParamProxy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.CharEncoding;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.write.builder.ExcelWriterBuilder;
import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
@Slf4j
@AllArgsConstructor
public class ExportExcelAspect {

    private static final String FILE_NAME_KEYWORDS = "fileName";

    private ConversionService mvcConversionService;

    private ObjectProvider<PageParamHandler<?>> pageParamHandlers;

    private ObjectProvider<Converter<?>> converterProvider;
    /**
     * 注入所有的 ResponseBodyAdvice，Spring 会自动按 @Order 排序
     */
    private ObjectProvider<ResponseBodyAdvice<?>> responseBodyAdvices;

    /**
     * 注入消息转换器，用于模拟 Spring MVC 的 MediaType 选择逻辑
     */
    private HttpMessageConverters messageConverters;

    /**
     * 游标分页拦截器（可选，仅当 MyBatis 在 classpath 时存在）
     */
    private ObjectProvider<CursorPaginationInterceptor> cursorInterceptorProvider;

    private ObjectProvider<CellWriteHandler> cellWriteHandlers;
    /**
     * 游标元数据解析器：扫描 VO 上 @CursorField/@CursorEntity 并缓存
     */
    private CursorMetadataResolver cursorMetadataResolver;


    @Around("@annotation(exportExcel)")
    public Object around(ProceedingJoinPoint point, ExportExcel exportExcel) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes());
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = Objects.requireNonNull(attributes.getResponse());
        if (!ExcelContext.isActive()) {
            // 非导出请求，放行
            return point.proceed();
        }
        try {
            Method method = ((MethodSignature) point.getSignature()).getMethod();
            // 文件名，优先请求参数，其次使用注解配置
            String fileName = request.getParameter(FILE_NAME_KEYWORDS);
            fileName = URLEncoder.encode(fileName != null ? fileName : exportExcel.fileName(), StandardCharsets.UTF_8.name());
            String sheetName = exportExcel.sheetName();

            Class<?> voClass = getActualVoType(method);
            int batchSize = exportExcel.batchSize();
            long maxSize = exportExcel.limit();
            CursorMetadata cursorMetadata = cursorMetadataResolver.resolve(voClass);
            boolean useCursor = cursorMetadata != null;
            if (useCursor && cursorInterceptorProvider.getIfAvailable() == null) {
                // 游标分页模式：VO中配置了@CursorClass、@CursorField 且 CursorPaginationInterceptor 已注册时启用
                log.warn("当前环境未使用mybatis，CursorPaginationInterceptor未生效, 无法开启游标分页导出，已降级为传统分页导出！");
                useCursor = false;
            }
            PageParamProxy<?> pageParamProxy = findPageableParam(point.getArgs());
            boolean isPageQuery = pageParamProxy != null;
            MethodParameter returnType = new MethodParameter(method, -1);
            // 设置响应头
            setExcelResponse(response, fileName);
            if (useCursor) {
                // 1、游标分页：将 pageSize 设为 -1，使 MyBatis-Plus 分页拦截器跳过
                if (isPageQuery) {
                    pageParamProxy.setPageParam(1, -1);
                }
                cursorBatchWrite(point, batchSize, maxSize, voClass, cursorMetadata, sheetName, request, response, returnType);
            } else if (isPageQuery) {
                // 2、传统分页
                batchWrite(point, pageParamProxy, batchSize, maxSize, voClass, sheetName, request, response, returnType);
            } else {
                // 3、一次性全量导出
                log.debug("进行一次性全量导出");
                fullWrite(point, voClass, maxSize, sheetName, request, response, returnType);
            }
            return null;
        } finally {
            ExcelContext.clear();
        }
    }

    /**
     * 游标分页：通过 ThreadLocal 把游标状态传给 MyBatis 拦截器，对用户业务代码完全无感知。
     * 多字段复合游标时由 {@link CursorMetadata#extractLastIds(Object)} 按顺序提取各列的 lastId。
     */
    private void cursorBatchWrite(ProceedingJoinPoint point,
                                  int batchSize, long maxSize,
                                  Class<?> voClass,
                                  CursorMetadata metadata,
                                  String sheetName,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  MethodParameter returnType) throws Throwable {
        try (ExcelWriter excelWriter = newExcelWriter(voClass, response)) {
            WriteSheet writeSheet = FesodSheet.writerSheet(sheetName).build();
            ExcelContext.activateCursorMode(metadata.dbColumns(), metadata.voFieldNames(), batchSize,
                    voClass, metadata.entityClass());
            long totalCount = 0;
            try {
                while (true) {
                    List<?> data = fetchBatch(point, returnType, request, response);
                    if (data.isEmpty()) {
                        break;
                    }
                    totalCount += data.size();
                    checkMaxSize(totalCount, maxSize);
                    excelWriter.write(data, writeSheet);
                    if (data.size() < batchSize) {
                        break;
                    }
                    CursorState state = ExcelContext.get();
                    Object[] lastIds = metadata.extractLastIdsFromBatch(data, state.getStartIndex());
                    ExcelContext.updateLastIds(lastIds);
                    if (log.isDebugEnabled()) {
                        log.debug("游标分页导出进度：已写入{}条，当前lastIds={}", totalCount, Arrays.toString(ExcelContext.get().getLastIds()));
                    }
                }
                log.info("游标分页导出完成，总数据量：{}条", totalCount);
            } catch (Exception e) {
                writeAbortedTip(excelWriter, writeSheet, e);
            }
        }
    }

    /**
     * 传统偏移量分页：循环调用接口，每次以 pageNum + batchSize 翻页。
     */
    private void batchWrite(ProceedingJoinPoint point,
                            PageParamProxy<?> pageParamProxy,
                            int batchSize, long maxSize,
                            Class<?> voClass,
                            String sheetName,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            MethodParameter returnType) throws Throwable {
        try (ExcelWriter excelWriter = newExcelWriter(voClass, response)) {
            WriteSheet writeSheet = FesodSheet.writerSheet(sheetName).build();
            int pageNum = 1;
            long totalCount = 0;
            try {
                while (true) {
                    pageParamProxy.setPageParam(pageNum, batchSize);
                    List<?> data = fetchBatch(point, returnType, request, response);
                    if (data.isEmpty()) {
                        break;
                    }
                    totalCount += data.size();
                    checkMaxSize(totalCount, maxSize);
                    excelWriter.write(data, writeSheet);
                    if (data.size() < batchSize) {
                        break;
                    }
                    pageNum++;
                }
            } catch (Exception e) {
                writeAbortedTip(excelWriter, writeSheet, e);
            }
        }
    }

    /**
     * 全量查询，一次性写入。
     */
    private void fullWrite(ProceedingJoinPoint point,
                           Class<?> voClass,
                           long maxSize,
                           String sheetName,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           MethodParameter returnType) throws Throwable {
        List<?> data = fetchBatch(point, returnType, request, response);
        log.info("全量查询数据一次性写入excel，数据量：{}条", data.size());
        checkMaxSize(data.size(), maxSize);
        ExcelWriterBuilder excelWriterBuilder = FesodSheet.write(response.getOutputStream(), voClass);
        // 注入自定义单元格写处理器
        cellWriteHandlers.orderedStream().forEach(excelWriterBuilder::registerWriteHandler);
        // 注入自定义数据转换器
        converterProvider.stream().forEach(excelWriterBuilder::registerConverter);
        excelWriterBuilder.sheet(sheetName).doWrite(data);
    }

    /**
     * 执行一次目标方法 + 应用ResponseBodyAdvice + 抽取List数据
     */
    private List<?> fetchBatch(ProceedingJoinPoint point,
                               MethodParameter returnType,
                               HttpServletRequest request,
                               HttpServletResponse response) throws Throwable {
        Object result = point.proceed();
        result = applyResponseBodyAdvices(result, returnType, request, response);
        return extractData(result);
    }

    /**
     * 构造一个支持自适应列宽 + 用户自定义转换器的 ExcelWriter。
     */
    private ExcelWriter newExcelWriter(Class<?> voClass, HttpServletResponse response) throws IOException {
        ExcelWriterBuilder builder = FesodSheet.write(response.getOutputStream(), voClass);
        // 注入自定义单元格写处理器
        cellWriteHandlers.orderedStream().forEach(builder::registerWriteHandler);
        // 注入自定义数据转换器
        converterProvider.orderedStream().forEach(builder::registerConverter);
        return builder.build();
    }

    /**
     * 文件已经开始下载、无法撤回时，把错误信息写到当前 Sheet。
     */
    private void writeAbortedTip(ExcelWriter excelWriter, WriteSheet writeSheet, Exception e) {
        log.warn("分批导出时发生异常", e);
        excelWriter.write(Collections.singleton(Collections.singleton("⚠️" + e.getMessage() + "，已终止下载，该数据不完整！")), writeSheet);
    }

    /**
     * 手动按 @Order 顺序执行所有匹配的 ResponseBodyAdvice。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object applyResponseBodyAdvices(Object body, MethodParameter returnType, HttpServletRequest request, HttpServletResponse response) {
        ServerHttpRequest serverRequest = new ServletServerHttpRequest(request);
        ServerHttpResponse serverResponse = new ServletServerHttpResponse(response);
        for (ResponseBodyAdvice advice : responseBodyAdvices) {
            if (advice.supports(returnType, MappingJackson2HttpMessageConverter.class)) {
                body = advice.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class, serverRequest, serverResponse);
            }
        }
        return body;
    }

    private static Class<?> getActualVoType(Method method) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        // 递归解析：一直取第一个泛型参数，直到拿到最终的非泛型 Class
        while (returnType.hasGenerics()) {
            returnType = returnType.getGeneric(0);
        }
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
                // 走的是 spring 官方的 ObjectToCollectionConvert，没有自定义的转换器，报错
                throw new IllegalArgumentException("不支持的返回类型: " + resultClass.getName() + "，请自定义转换器实现ExcelDataConverter接口，并注入spring容器");
            }
            return convert;
        }
        throw new IllegalArgumentException("不支持的返回类型: " + resultClass.getName() + "，请自定义转换器实现ExcelDataConverter接口，并注入spring容器");
    }
}
