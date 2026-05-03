package io.github.luozhan.excel.page;

import lombok.AllArgsConstructor;

/**
 * 分页参数代理
 *
 * @param <T> 实际page类
 * @author luozhan
 * @since 2026-05-03
 */
@AllArgsConstructor
public class PageParamProxy<T> {

    private final T pageDTO;
    private final PageParamHandler<T> pageParamHandler;


    public void setPageParam(int pageNum, int pageSize) {
        pageParamHandler.accept(pageDTO, pageNum, pageSize);
    }


}
