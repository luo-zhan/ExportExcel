package io.github.luozhan.excel.integration;

import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 集成测试专用 VO（不含 @CursorField，避免触发游标分页改写）。
 * <p>
 * 字段定义对齐 {@code DemoVO}，但刻意去掉游标注解，
 * 用于覆盖传统偏移量分页 / 全量导出 / limit 超限等场景。
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SimpleDemoVO {

    // 有swagger注解，省略@ExcelProperty也可以
    @ApiModelProperty("员工姓名")
    private String name;

    @ExcelProperty("年龄")
    private Integer age;

    @ExcelProperty("是否在职")
    private Boolean active;

    @ExcelProperty("所属部门")
    private String department;

    @ExcelProperty("电子邮箱地址")
    private String email;

    @ExcelProperty("联系电话")
    private String phone;

    @ExcelProperty("家庭住址")
    private String address;

    @ExcelProperty("职位")
    private String position;

    @ExcelProperty("工号")
    private String employeeNo;

    @ExcelProperty("个人简介")
    private String bio;

    @ExcelProperty("生日")
    private Date birth;

    @ExcelProperty("收入")
    private BigDecimal income;
}
