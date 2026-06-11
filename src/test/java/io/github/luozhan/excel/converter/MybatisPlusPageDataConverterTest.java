package io.github.luozhan.excel.converter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.luozhan.excel.paramhandle.rsp.impl.MybatisPlusPageDataConverter;
import io.github.luozhan.excel.sample.model.DemoVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * {@link MybatisPlusPageDataConverter} 单元测试：验证从 {@link IPage} 提取记录集合的逻辑。
 */
class MybatisPlusPageDataConverterTest {

    private final MybatisPlusPageDataConverter converter = new MybatisPlusPageDataConverter();

    @Test
    @DisplayName("应返回 IPage 中的记录列表")
    void shouldReturnRecordsFromIPage() {
        DemoVO vo1 = new DemoVO();
        DemoVO vo2 = new DemoVO();
        List<DemoVO> records = Arrays.asList(vo1, vo2);
        IPage<DemoVO> iPage = mock(IPage.class);
        when(iPage.getRecords()).thenReturn(records);

        List<?> result = converter.convert(iPage);

        assertEquals(records, result);
        verify(iPage).getRecords();
    }

    @Test
    @DisplayName("IPage 记录为空时应返回空集合")
    void shouldReturnEmptyListWhenIPageHasNoRecords() {
        IPage<DemoVO> iPage = mock(IPage.class);
        when(iPage.getRecords()).thenReturn(Collections.emptyList());

        List<?> result = converter.convert(iPage);

        assertTrue(result.isEmpty());
    }
}
