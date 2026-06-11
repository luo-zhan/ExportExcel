package io.github.luozhan.excel.cursor.integration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 游标分页集成测试用Mapper
 */
@Mapper
public interface CursorDemoMapper {

    @Select("SELECT id, name, age FROM demo")
    List<CursorDemoVO> selectAll();
}
