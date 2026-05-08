package io.github.luozhan.excel.paramhandle.rsp.impl;

import io.github.luozhan.excel.paramhandle.rsp.ExcelDataConverter;
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
