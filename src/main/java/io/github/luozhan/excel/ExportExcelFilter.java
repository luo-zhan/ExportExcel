package io.github.luozhan.excel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;

public class ExportExcelFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ExportExcelFilter.class);
    private static final String EXPORT_SUFFIX = "/export";
    public static final String EXPORT_FLAG_ATTRIBUTE = "is_export_request";

    private final RequestMappingHandlerMapping handlerMapping;

    public ExportExcelFilter(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithinApp = requestURI.substring(contextPath.length());

        if (!pathWithinApp.endsWith(EXPORT_SUFFIX)) {
            // 不是/export结尾，放行
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (lookupHandlerMethod(request)!=null) {
            // 导出接口已经申明，直接请求
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 原查询接口，即去掉导出后缀的path
        String queryPath = pathWithinApp.substring(0, pathWithinApp.length() - EXPORT_SUFFIX.length());
        log.info("收到Excel导出请求：{}", pathWithinApp);
        // 构造查询接口请求，寻找spring容器中注册的接口
        ExportHttpServletRequestWrapper requestWrapper = new ExportHttpServletRequestWrapper(request, contextPath, queryPath);
        ServletRequestPathUtils.parseAndCache(requestWrapper);
        HandlerMethod handlerMethod = lookupHandlerMethod(requestWrapper);
        if (handlerMethod == null) {
            // 找不到导出接口，也找不到查询接口，放行（正常走404逻辑）
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        Method method = handlerMethod.getMethod();
        if (!method.isAnnotationPresent(ExportExcel.class)) {
            log.warn("查询接口{}已找到，但未标注@ExportExcel注解, 已跳过", queryPath);
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        request.setAttribute(EXPORT_FLAG_ATTRIBUTE, Boolean.TRUE);
        // 转发到查询接口
        filterChain.doFilter(requestWrapper, servletResponse);
    }

    private HandlerMethod lookupHandlerMethod(HttpServletRequest originalRequest) {
        try {
            // 复用Spring官方原生匹配逻辑，覆盖所有路径规则、请求条件
            // 构造Mock请求，完全对齐Spring的匹配入参，避免路径匹配偏差
            HandlerExecutionChain handlerChain = handlerMapping.getHandler(originalRequest);
            // 仅返回Controller对应的接口方法，过滤静态资源、自定义处理器等非目标对象
            if (handlerChain != null && handlerChain.getHandler() instanceof HandlerMethod) {
                return (HandlerMethod) handlerChain.getHandler();
            }
        } catch (Exception e) {
            // 匹配不到接口会抛出NoHandlerFoundException，属于正常业务场景，仅打debug日志避免刷屏
            log.debug("spring容器中找不到url path: {}", originalRequest.getRequestURI());
        }
        return null;
    }

    static class ExportHttpServletRequestWrapper extends HttpServletRequestWrapper {

        private final String newRequestURI;
        private final String newServletPath;

        ExportHttpServletRequestWrapper(HttpServletRequest request, String contextPath, String originalPath) {
            super(request);
            this.newServletPath = originalPath;
            this.newRequestURI = contextPath + originalPath;
        }

        @Override
        public String getRequestURI() {
            return newRequestURI;
        }

        @Override
        public String getServletPath() {
            return newServletPath;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
