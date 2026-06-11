package io.github.luozhan.excel.converter;


import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.enums.CellDataTypeEnum;
import org.apache.fesod.sheet.metadata.GlobalConfiguration;
import org.apache.fesod.sheet.metadata.data.ReadCellData;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.metadata.property.ExcelContentProperty;

/**
 * Long to String转换器，解决Long类型15位之后就会丢失精度的问题
 *
 * @author luozhan
 * @since 2026/06/11
 */
public class LongToStringConverter implements Converter<Long> {
    @Override
    public Class<?> supportJavaTypeKey() {
        return Long.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        // 返回null表示匹配所有Excel类型
        return null;
    }

    @Override
    public WriteCellData<?> convertToExcelData(Long value, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
        return new WriteCellData<>(value == null ? "" : value.toString());
    }

    @Override
    public Long convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) throws Exception {
        String value = cellData.getStringValue();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }
}