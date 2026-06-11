package io.github.luozhan.excel.cursor;

import java.lang.annotation.*;

/**
 * 游标分页字段标记注解
 * <p>
 * 标注在导出VO的字段上，用于声明该字段为游标分页字段。
 * 只要VO类中存在被该注解标注的字段，导出时即自动启用游标分页（{@code WHERE {dbColumn} > lastId}）。
 * <p>
 * 数据库列名解析规则：
 * <ul>
 *     <li>{@link #value()} 非空：直接使用其作为数据库列名，支持表别名前缀（如 {@code "t.id"}、{@code "u.create_time"}）</li>
 *     <li>{@link #value()} 留空：使用VO字段名，驼峰会自动转换为下划线（如 {@code employeeNo} → {@code employee_no}）</li>
 * </ul>
 * <p>
 * <b>多字段复合游标</b>：在 VO 中给多个字段同时标注 {@link CursorField}，每个字段一处注解；
 * 通过 {@link #order()} 显式声明各字段在元组中的位置，拦截器据此生成
 * {@code WHERE (col1, col2) > (?, ?) ORDER BY col1 ASC, col2 ASC}。
 * 这种方式不依赖任何命名约定，VO字段与数据库列的映射完全显式。
 * <p>
 * <b>VO 与实体类不一致</b>：当 Mapper 返回 DO/Entity 后再转 VO 时，请在 VO 类上额外标注 {@link CursorEntity}
 * 指定真实返回类型，使拦截器能准确命中目标 SQL。
 * <p>
 * 该字段同时用于：
 * <ol>
 *     <li>SQL改造：拦截器据此追加 {@code WHERE} 条件、{@code ORDER BY} 子句</li>
 *     <li>lastId提取：导出循环中从上一批最后一条记录的VO字段读取游标值（多字段时按 order 顺序分别提取）</li>
 * </ol>
 *
 * @author luozhan
 * @since 2026-06-08
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CursorField {

    /**
     * 数据库列名（可选）
     * <p>
     * 当VO字段名与数据库列名不一致、或多表查询需要表别名前缀时，通过该属性显式指定。
     * 留空则取注解所在VO字段名并自动驼峰转下划线。
     */
    String value() default "";

    /**
     * 多字段复合游标时的排序位置（从0开始），决定元组中各列的先后顺序。
     * <p>
     * 单字段游标时可忽略；多字段时必须显式声明且不重复。
     * 示例：
     * <pre>
     * {@code @CursorField(value = "create_time", order = 0)}
     * private Date createTime;
     *
     * {@code @CursorField(value = "id", order = 1)}
     * private Long id;
     * </pre>
     * 将生成 {@code WHERE (create_time, id) > (?, ?) ORDER BY create_time ASC, id ASC}。
     */
    int order() default -1;
}
