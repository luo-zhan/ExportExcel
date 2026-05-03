package io.github.luozhan.excel.converter;

import org.springframework.core.convert.converter.Converter;

import java.util.List;

/**
 * Excel数据转换器接口
 * 声明如何从接口方法返回参数中取出List数据
 *
 * @author luozhan
 * @since 2026-05-03
 */
public interface ExcelDataConverter<S> extends Converter<S, List<?>> {
}
