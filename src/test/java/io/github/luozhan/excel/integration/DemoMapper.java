package io.github.luozhan.excel.integration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 集成测试用 Mapper，从 H2 内存表 demo_user 中读取测试数据。
 * <p>
 * 返回 {@link SimpleDemoVO}（不含 @CursorField），
 * 让 ExportExcelAspect 走传统分页 / 全量路径，验证非游标场景的导出逻辑。
 */
@Mapper
public interface DemoMapper {

    String COLUMNS = "name, age, active, department, email, phone, address, position, employee_no, bio, birth, income";

    @Select("SELECT " + COLUMNS + " FROM demo_user ORDER BY employee_no")
    List<SimpleDemoVO> selectAll();

    @Select("SELECT " + COLUMNS + " FROM demo_user ORDER BY employee_no LIMIT #{size} OFFSET #{offset}")
    List<SimpleDemoVO> selectPage(@Param("offset") long offset, @Param("size") long size);

    @Select("SELECT " + COLUMNS + " FROM demo_user WHERE 1 = 0")
    List<SimpleDemoVO> selectEmpty();
}
