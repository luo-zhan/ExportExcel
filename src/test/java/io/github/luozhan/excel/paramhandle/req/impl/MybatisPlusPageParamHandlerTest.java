package io.github.luozhan.excel.paramhandle.req.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link MybatisPlusPageParamHandler} 单元测试。
 */
class MybatisPlusPageParamHandlerTest {

    private final MybatisPlusPageParamHandler handler = new MybatisPlusPageParamHandler();

    @Test
    @DisplayName("应将页码/页大小设置到 IPage，并禁用 count 查询")
    void shouldApplyPageNumAndSizeAndDisableCount() {
        IPage<?> page = mock(IPage.class);

        int pageNum = 1;
        int pageSize = 10;

        // 执行
        handler.accept(page, pageNum, pageSize);

        // 验证 3 个行为 100% 覆盖
        verify(page).setCurrent(pageNum);    // 验证设置页码
        verify(page).setSize(pageSize);      // 验证设置页大小
        verify(page).setTotal(-1);           // 验证设置 total=-1 关闭 count
    }
}
