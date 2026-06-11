package io.github.luozhan.excel.converter;

import io.github.luozhan.excel.paramhandle.rsp.impl.SpringPageDataConverter;
import io.github.luozhan.excel.sample.model.DemoVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * {@link SpringPageDataConverter} 单元测试：验证从 Spring {@link Page} 提取内容集合的逻辑。
 */
class SpringPageDataConverterTest {

    private final SpringPageDataConverter converter = new SpringPageDataConverter();

    @Test
    @DisplayName("应返回 Page 中的 content 列表")
    void shouldReturnContentFromPage() {
        DemoVO vo1 = new DemoVO();
        DemoVO vo2 = new DemoVO();
        List<DemoVO> content = Arrays.asList(vo1, vo2);
        @SuppressWarnings("unchecked")
        Page<DemoVO> page = mock(Page.class);
        when(page.getContent()).thenReturn(content);

        List<?> result = converter.convert(page);

        assertEquals(content, result);
        verify(page).getContent();
    }

    @Test
    @DisplayName("Page 内容为空时应返回空集合")
    void shouldReturnEmptyListWhenPageHasNoContent() {
        @SuppressWarnings("unchecked")
        Page<DemoVO> page = mock(Page.class);
        when(page.getContent()).thenReturn(Collections.emptyList());

        List<?> result = converter.convert(page);

        assertTrue(result.isEmpty());
    }
}
