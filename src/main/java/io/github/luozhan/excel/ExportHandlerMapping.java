package io.github.luozhan.excel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Excel导出HandlerMapping
 * <p>
 * 拦截所有以 /export 结尾的请求，重写路径后转发到原始接口
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100) // 优先级小于requestMappingHandlerMapping，大于URLHandlerMapping（避免被url/*全匹配捕获）
public class ExportHandlerMapping implements HandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(ExportHandlerMapping.class);
    private static final String EXPORT_SUFFIX = "/export";
    public static final String EXPORT_FLAG_ATTRIBUTE = "X-Export-Request";

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    // 构造器注入
    public ExportHandlerMapping(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }


    @Override
    public HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        // 去掉上下文路径，得到应用内路径
        String path = requestUri.substring(contextPath.length());
        // 不是 /export 结尾 → 跳过
        if (!path.endsWith(EXPORT_SUFFIX)) {
            return null;
        }
        // 原始接口路径（去掉 /export 后缀）
        String originalPath = path.substring(0, path.length() - EXPORT_SUFFIX.length());
        // 封装路径重写请求
        PathRewriteRequestWrapper wrappedRequest = new PathRewriteRequestWrapper(request, originalPath);
        // 继续丢给requestMappingHandlerMapping处理
        HandlerExecutionChain chain = requestMappingHandlerMapping.getHandler(wrappedRequest);
        if (chain == null) {
            log.error("原始接口 {} 不存在，跳过导出处理，请求url：{}", originalPath, request.getRequestURL());
            return null;
        }

        // 设置导出标记，后续AOP通过这个属性判断
        request.setAttribute(EXPORT_FLAG_ATTRIBUTE, Boolean.TRUE);
        log.info("📤 导出请求转发成功：{} → 映射到原生接口 {}", path, originalPath);
        return chain;
    }


}