package io.github.luozhan.excel.cursor.integration;

import io.github.luozhan.excel.cursor.CursorField;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;

/**
 * 游标分页集成测试用VO
 */
@Data
public class CursorDemoVO {

    @ExcelProperty("ID")
    @CursorField(value = "id", order = 0)
    private Long id;

    @ExcelProperty("姓名")
    @CursorField(value = "name", order = 1)
    private String name;

    @ExcelProperty("年龄")
    private Integer age;
}
