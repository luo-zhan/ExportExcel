package io.github.luozhan.excel.core;

import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.net.URI;

/**
 * 路径重写请求包装器
 *
 * @author R
 * @since 2026/6/3
 */
public class PathRewriteRequestWrapper extends HttpServletRequestWrapper {

    private final String PATH_ATTRIBUTE = ServletRequestPathUtils.class.getName() + ".PATH";

    private final String rewrittenPath;

    public PathRewriteRequestWrapper(HttpServletRequest request, String rewrittenPath) {
        super(request);
        this.rewrittenPath = rewrittenPath;
        rewriteRequestPath(request, rewrittenPath);
    }

    /**
     * 重写缓存的RequestPath对象
     */
    private void rewriteRequestPath(HttpServletRequest request, String rewrittenPath) {
        // 获取原始缓存的RequestPath
        RequestPath originalRequestPath = (RequestPath) request.getAttribute(PATH_ATTRIBUTE);
        if (originalRequestPath == null) {
            // 如果没有缓存（AntPathMatcher模式），直接返回null
            return;
        }
        String fullNewPath = request.getContextPath() + rewrittenPath;
        // 更新请求属性中的缓存值，spring通过这个获取请求路径
        RequestPath newPath = RequestPath.parse(URI.create(fullNewPath), request.getContextPath());
        request.setAttribute(PATH_ATTRIBUTE, newPath);
    }

    @Override
    public String getServletPath() {
        return rewrittenPath;
    }

    @Override
    public String getRequestURI() {
        return getContextPath() + rewrittenPath;
    }


}
