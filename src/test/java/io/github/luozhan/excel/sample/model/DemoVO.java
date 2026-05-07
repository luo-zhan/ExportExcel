package io.github.luozhan.excel.sample.model;

import lombok.*;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 示例VO对象
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DemoVO {

    @ExcelProperty("员工姓名")
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
    private BigDecimal in;

}
