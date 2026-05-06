package io.github.luozhan.excel;

import java.lang.annotation.*;

/**
 * 导出表格注解
 * <p>
 * 使用说明：
 * <p>1.在Controller原来的查询方法上标注@ExportExcel
 * <p>2.前端在原有url后面添加"/export"后缀即可触发Excel导出（如：/api/users -> /api/users/export）
 * <p>3.在返回VO中的属性上使用@ExcelProperty注解配置列名（和EasyExcel一样），如果有些属性不需要导出，在类上标记@ExcelIgnoreUnannotated 注解
 * <p>4.支持方法返回参数类型：List、IPage(MyBatis-Plus)、Page(Spring Data)等分页对象，其他类型需要自定义Converter并注入spring（参考SpringPageDataConverter）
 * <p>5.默认最大导出1万条数据，数据量太大会有深分页性能问题，建议考虑游标查询实现，然后通过limit调整阈值
 * <p>注：该组件生成excel会分批查询流式导出，内存占用很小，不必担心jvm压力
 *
 * @author luozhan
 * @since 2026-05-04
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExportExcel {

    /**
     * 文件名
     * 如果url参数有指定"fileName"则优先取url参数中的
     */
    String fileName() default "导出数据";

    /**
     * sheet命名
     */
    String sheetName() default "sheet1";

    /**
     * 导出数据上限
     * 数据量太大深分页问题导致性能较差，建议单独通过游标查询（cursor）实现，然后提高该阈值
     */
    long limit() default 10000;

    /**
     * 分批查询数量
     * 仅限接口方法有分页参数时生效
     */
    int batchSize() default 1000;

}
