package io.github.luozhan.excel.converter.impl;

import io.github.luozhan.excel.converter.ExcelDataConverter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring page对象转List
 *
 * @author luozhan
 * @since 2026-05-03
 */
public class SpringPageDataConverter implements ExcelDataConverter<Page<?>> {

    @Override
    public List<?> convert(Page<?> source) {
        return source.getContent();
    }
}
