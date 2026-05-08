package io.github.luozhan.excel.paramhandle.req.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.luozhan.excel.paramhandle.req.PageParamHandler;

/**
 * Mybatis-Plus 分页对象处理器
 *
 * @author luozhan
 * @since 2026-04-30
 */
public class MybatisPlusPageParamHandler extends PageParamHandler<IPage<?>> {

    @Override
    public void accept(IPage<?> page, int pageNumber, int pageSize) {
        page.setCurrent(pageNumber);
        page.setSize(pageSize);
        // 关闭总数查询，提高性能
        page.setTotal(-1);
    }
}
