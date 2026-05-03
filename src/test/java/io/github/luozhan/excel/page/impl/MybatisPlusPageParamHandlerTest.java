package io.github.luozhan.excel.page.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MybatisPlusPageParamHandlerTest {

    private final MybatisPlusPageParamHandler handler = new MybatisPlusPageParamHandler();

    @Test
    void convert_shouldDelegateSetPageNumToIPageSetCurrent() {
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
