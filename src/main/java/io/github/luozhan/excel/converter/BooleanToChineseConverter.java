package io.github.luozhan.excel.converter;

import org.apache.fesod.sheet.converters.ReadConverterContext;
import org.apache.fesod.sheet.converters.WriteConverterContext;
import org.apache.fesod.sheet.converters.booleanconverter.BooleanStringConverter;
import org.apache.fesod.sheet.metadata.data.WriteCellData;

/**
 * Boolean->是/否 转换器
 *
 * @author luozhan
 * @since 2026/5/8
 */
public class BooleanToChineseConverter extends BooleanStringConverter {

    private final String trueFlag = "是";
    private final String falseFlag = "否";

    /**
     * 读取Excel：「是/否」转Boolean
     */
    @Override
    public Boolean convertToJavaData(ReadConverterContext<?> context) {
        String cellValue = context.getReadCellData().getStringValue();
        if (trueFlag.equals(cellValue)) {
            return true;
        }
        if (falseFlag.equals(cellValue)) {
            return false;
        }
        return null;
    }

    /**
     * 写入Excel：Boolean转「是/否」
     */
    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<Boolean> context) {
        Boolean value = context.getValue();
        if (Boolean.TRUE.equals(value)) {
            return new WriteCellData<>(trueFlag);
        }
        if (Boolean.FALSE.equals(value)) {
            return new WriteCellData<>(falseFlag);
        }
        return new WriteCellData<>("");
    }


}
