package io.github.luozhan.excel.converter.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.luozhan.excel.converter.ExcelDataConverter;

import java.util.List;

/**
 * MybatisPlus分页对象转List
 *
 * @author luozhan
 * @since 2026-05-03
 */
public class MybatisPlusPageDataConverter implements ExcelDataConverter<IPage<?>> {

    @Override
    public List<?> convert(IPage<?> source) {
        return source.getRecords();
    }
}
