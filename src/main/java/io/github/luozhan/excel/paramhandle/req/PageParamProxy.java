package io.github.luozhan.excel.paramhandle.req;

/**
 * 分页参数代理
 *
 * @param <T> 实际page类
 * @author luozhan
 * @since 2026-05-03
 */
public class PageParamProxy<T> {

    private final T pageDTO;
    private final PageParamHandler<T> pageParamHandler;

    public PageParamProxy(T pageDTO, PageParamHandler<T> pageParamHandler) {
        this.pageDTO = pageDTO;
        this.pageParamHandler = pageParamHandler;
    }

    public void setPageParam(int pageNum, int pageSize) {
        pageParamHandler.accept(pageDTO, pageNum, pageSize);
    }


}
