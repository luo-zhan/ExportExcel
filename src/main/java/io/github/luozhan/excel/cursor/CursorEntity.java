package io.github.luozhan.excel.cursor;

import java.lang.annotation.*;

/**
 * VO 与 Mapper/SQL 实际返回实体类型的映射声明（类级注解）
 * <p>
 * 当 Mapper 返回 DO/Entity、Service 层再转成 VO 时，游标拦截器无法通过返回类型直接命中目标 VO。
 * 在 VO 类上声明该注解，告知拦截器目标 SQL 的真实返回类型，使其能够准确识别需要改写的查询。
 * <p>
 * 与 {@link CursorField} 的职责区分：
 * <ul>
 *     <li>{@link CursorField}：字段级注解，描述<b>每个游标字段</b>如何映射到数据库列、以及在元组中的顺序</li>
 *     <li>{@link CursorEntity}：类级注解，描述<b>整个 VO</b> 与 Mapper 返回实体类型的映射关系</li>
 * </ul>
 * <p>
 * 示例：
 * <pre>{@code
 * @Data
 * @CursorEntity(EmployeeDO.class)
 * public class EmployeeVO {
 *     @CursorField(value = "createTime", order = 0)
 *     private Date createTime;
 *
 *     @CursorField(value = "id", order = 1)
 *     private Long id;
 * }
 * }</pre>
 * <p>
 *
 * @author luozhan
 * @since 2026-06-10
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CursorEntity {

    /**
     * Mapper/SQL 实际返回的实体类型，如果和VO类型一致，可不填
     */
    Class<?> value() default void.class;
}
