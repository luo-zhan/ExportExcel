package io.github.luozhan.excel.writehandle;

import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.apache.poi.ss.usermodel.Cell;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 通过Swagger的@ApiModelProperty或者@Schema来读取列名，从而实现省略@ExcelProperty重复配置字段名
 * 优先级：@ExcelProperty.value > @ApiModelProperty或@Schema.value > 字段名（英文）
 *
 * @author mmhm
 * @since 2026/6/15
 */
public class SwaggerPropertySimpleWriteHandler implements CellWriteHandler {

    private static final String API_MODEL_PROPERTY_CLASS = "io.swagger.annotations.ApiModelProperty";
    private static final String SCHEMA_CLASS = "io.swagger.v3.oas.annotations.media.Schema";

    private static final Class<? extends Annotation> API_MODEL_PROPERTY = loadAnnotationClass(API_MODEL_PROPERTY_CLASS);
    private static final Class<? extends Annotation> SCHEMA = loadAnnotationClass(SCHEMA_CLASS);

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        // 只处理表头
        if (!Boolean.TRUE.equals(context.getHead())) {
            return;
        }
        if (context.getHeadData() == null || context.getHeadData().getField() == null) {
            return;
        }
        Field field = context.getHeadData().getField();
        String headName = resolveHeadName(field);
        if (headName == null) {
            return;
        }
        Cell cell = context.getCell();
        if (cell == null) {
            return;
        }
        cell.setCellValue(headName);
    }

    private String resolveHeadName(Field field) {
        ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
        if (excelProperty != null && hasEffectiveValue(excelProperty.value())) {
            // @ExcelProperty 优先级最高；Fesod 已将其值写入表头，无需覆盖
            return null;
        }
        String swaggerValue = readSwaggerValue(field);
        if (swaggerValue != null) {
            return swaggerValue;
        }
        return field.getName();
    }

    private boolean hasEffectiveValue(String[] values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String readSwaggerValue(Field field) {
        String value = readAnnotationStringAttribute(field, API_MODEL_PROPERTY, "value");
        if (value != null) {
            return value;
        }
        value = readAnnotationStringAttribute(field, SCHEMA, "description");
        if (value != null) {
            return value;
        }
        return readAnnotationStringAttribute(field, SCHEMA, "title");
    }

    private String readAnnotationStringAttribute(Field field, Class<? extends Annotation> annotationType,
                                                 String attributeName) {
        if (annotationType == null || field == null) {
            return null;
        }
        Annotation annotation = field.getAnnotation(annotationType);
        if (annotation == null) {
            return null;
        }
        try {
            Method method = annotationType.getMethod(attributeName);
            Object result = method.invoke(annotation);
            if (result instanceof String) {
                String str = (String) result;
                return str.trim().isEmpty() ? null : str;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadAnnotationClass(String className) {
        try {
            return (Class<? extends Annotation>) Class.forName(className);
        } catch (ClassNotFoundException | ClassCastException ignored) {
            return null;
        }
    }
}
