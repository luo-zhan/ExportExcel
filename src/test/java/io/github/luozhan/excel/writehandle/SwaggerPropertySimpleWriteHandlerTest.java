package io.github.luozhan.excel.writehandle;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.annotation.ExcelIgnore;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SwaggerPropertySimpleWriteHandler} 单元测试。
 */
class SwaggerPropertySimpleWriteHandlerTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("使用 @ExcelProperty 时，优先使用 @ExcelProperty.value 作为列名")
    void shouldPreferExcelPropertyWhenPresent() throws Exception {
        List<String> headers = writeAndReadHeaders(ExcelPropertyVo.class,
                Collections.singletonList(new ExcelPropertyVo("张三", 18)));

        assertEquals(Arrays.asList("Excel姓名", "Excel年龄"), headers);
    }

    @Test
    @DisplayName("未使用 @ExcelProperty 时，根据 Swagger 注解生成列名")
    void shouldUseSwaggerAnnotationWhenExcelPropertyMissing() throws Exception {
        List<String> headers = writeAndReadHeaders(SwaggerOnlyVo.class,
                Collections.singletonList(new SwaggerOnlyVo("张三", 18, "标题", "plainValue")));

        assertEquals(Arrays.asList("Swagger姓名", "Swagger年龄", "Swagger标题", "plain"), headers);
    }

    @Test
    @DisplayName("字段标记 @ExcelIgnore 后，不会写入 Excel 表格")
    void shouldIgnoreExcelIgnoreField() throws Exception {
        List<String> headers = writeAndReadHeaders(SwaggerIgnoreVo.class,
                Collections.singletonList(new SwaggerIgnoreVo("张三", "secret")));

        assertEquals(Collections.singletonList("Swagger姓名"), headers);
    }

    private List<String> writeAndReadHeaders(Class<?> voClass, List<?> data) throws Exception {
        File file = new File(tempDir, voClass.getSimpleName() + ".xlsx");

        FesodSheet.write(file, voClass)
                .registerWriteHandler(new SwaggerPropertySimpleWriteHandler())
                .sheet("sheet1")
                .doWrite(data);

        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            return StreamSupport.stream(headerRow.spliterator(), false)
                    .map(Cell::getStringCellValue)
                    .collect(Collectors.toList());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ExcelPropertyVo {

        @ExcelProperty("Excel姓名")
        @ApiModelProperty("Swagger姓名")
        private String name;

        @ExcelProperty("Excel年龄")
        @Schema(description = "Swagger年龄")
        private Integer age;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SwaggerOnlyVo {

        @ApiModelProperty("Swagger姓名")
        private String name;

        @Schema(description = "Swagger年龄")
        private Integer age;

        @Schema(title = "Swagger标题")
        private String title;

        private String plain;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SwaggerIgnoreVo {

        @ApiModelProperty("Swagger姓名")
        private String name;

        @ExcelIgnore
        @ApiModelProperty("Swagger密码")
        private String password;
    }
}
