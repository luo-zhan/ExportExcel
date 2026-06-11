package io.github.luozhan.excel.cursor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CursorPaginationInterceptor} 的 SQL 改造逻辑单元测试。
 * <p>
 * 验证各种 SQL 场景下游标条件注入是否正确，以 {@link Nested} 按场景分组。
 */
class CursorPaginationInterceptorTest {

    private final CursorPaginationInterceptor interceptor = new CursorPaginationInterceptor();

    @Nested
    @DisplayName("简单查询")
    class SimpleQuery {

        @Test
        @DisplayName("无WHERE无ORDER BY无LIMIT")
        void shouldAddWhereAndOrderByAndLimit() {
            String sql = "SELECT * FROM user";
            String result = interceptor.rebuildSql(sql, "id", 0, 1000);
            assertEquals("SELECT * FROM user WHERE id > 0 ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("有LIMIT无WHERE")
        void shouldStripLimitAndAddCursorCondition() {
            String sql = "SELECT * FROM user LIMIT 10";
            String result = interceptor.rebuildSql(sql, "id", 100, 500);
            assertEquals("SELECT * FROM user WHERE id > 100 ORDER BY id ASC LIMIT 500", result);
        }

        @Test
        @DisplayName("有LIMIT和OFFSET")
        void shouldStripLimitOffset() {
            String sql = "SELECT * FROM user LIMIT 10 OFFSET 20";
            String result = interceptor.rebuildSql(sql, "id", 200, 1000);
            assertEquals("SELECT * FROM user WHERE id > 200 ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("MySQL风格LIMIT x,y")
        void shouldStripMySqlStyleLimit() {
            String sql = "SELECT * FROM user LIMIT 20, 10";
            String result = interceptor.rebuildSql(sql, "id", 300, 1000);
            assertEquals("SELECT * FROM user WHERE id > 300 ORDER BY id ASC LIMIT 1000", result);
        }
    }

    @Nested
    @DisplayName("有WHERE条件")
    class WithWhereClause {

        @Test
        @DisplayName("已有WHERE条件追加AND")
        void shouldAppendAndCondition() {
            String sql = "SELECT * FROM user WHERE status = 1";
            String result = interceptor.rebuildSql(sql, "id", 500, 1000);
            assertEquals("SELECT * FROM user WHERE status = 1 AND id > 500 ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("WHERE + ORDER BY + LIMIT全部替换")
        void shouldReplaceOrderByAndLimit() {
            String sql = "SELECT * FROM user WHERE status = 1 ORDER BY create_time DESC LIMIT 10 OFFSET 0";
            String result = interceptor.rebuildSql(sql, "idName", 1000, 500);
            assertEquals("SELECT * FROM user WHERE status = 1 AND id_name > 1000 ORDER BY id_name ASC LIMIT 500", result);
        }

        @Test
        @DisplayName("复杂WHERE条件")
        void shouldHandleComplexWhereCondition() {
            String sql = "SELECT * FROM user WHERE status = 1 AND age > 18 AND name LIKE '%test%' ORDER BY id LIMIT 20";
            String result = interceptor.rebuildSql(sql, "id", 99, 1000);
            assertEquals("SELECT * FROM user WHERE status = 1 AND age > 18 AND name LIKE '%test%' AND id > 99 ORDER BY id ASC LIMIT 1000", result);
        }
    }

    @Nested
    @DisplayName("多表JOIN查询")
    class JoinQuery {

        @Test
        @DisplayName("JOIN查询使用表别名前缀")
        void shouldSupportTableAlias() {
            String sql = "SELECT t.*, d.name FROM user t JOIN dept d ON t.dept_id = d.id WHERE t.status = 1 ORDER BY t.create_time LIMIT 10";
            String result = interceptor.rebuildSql(sql, "t.id", 500, 1000);
            assertEquals("SELECT t.*, d.name FROM user t JOIN dept d ON t.dept_id = d.id WHERE t.status = 1 AND t.id > 500 ORDER BY t.id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("JOIN无WHERE条件")
        void shouldAddWhereInJoinQuery() {
            String sql = "SELECT t.* FROM user t LEFT JOIN dept d ON t.dept_id = d.id";
            String result = interceptor.rebuildSql(sql, "t.id", 0, 2000);
            assertEquals("SELECT t.* FROM user t LEFT JOIN dept d ON t.dept_id = d.id WHERE t.id > 0 ORDER BY t.id ASC LIMIT 2000", result);
        }
    }

    @Nested
    @DisplayName("子查询场景")
    class SubQuery {

        @Test
        @DisplayName("不影响子查询中的WHERE")
        void shouldNotAffectSubQueryWhere() {
            String sql = "SELECT * FROM (SELECT * FROM user WHERE id > 0) t LIMIT 10";
            String result = interceptor.rebuildSql(sql, "t.id", 100, 1000);
            assertEquals("SELECT * FROM (SELECT * FROM user WHERE id > 0) t WHERE t.id > 100 ORDER BY t.id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("不影响子查询中的ORDER BY")
        void shouldNotAffectSubQueryOrderBy() {
            String sql = "SELECT * FROM (SELECT * FROM user ORDER BY create_time) t WHERE t.status = 1 ORDER BY t.id LIMIT 10";
            String result = interceptor.rebuildSql(sql, "t.id", 200, 500);
            assertEquals("SELECT * FROM (SELECT * FROM user ORDER BY create_time) t WHERE t.status = 1 AND t.id > 200 ORDER BY t.id ASC LIMIT 500", result);
        }
    }

    @Nested
    @DisplayName("GROUP BY / HAVING 场景")
    class GroupByQuery {

        @Test
        @DisplayName("WHERE在GROUP BY之前插入游标条件")
        void shouldInsertBeforeGroupBy() {
            String sql = "SELECT dept, COUNT(*) as cnt FROM user WHERE status = 1 GROUP BY dept";
            String result = interceptor.rebuildSql(sql, "id", 100, 1000);
            assertEquals("SELECT dept, COUNT(*) AS cnt FROM user WHERE status = 1 AND id > 100 GROUP BY dept ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("无WHERE时在GROUP BY之前插入")
        void shouldAddWhereBeforeGroupBy() {
            String sql = "SELECT dept, COUNT(*) FROM user GROUP BY dept HAVING COUNT(*) > 5";
            String result = interceptor.rebuildSql(sql, "id", 0, 1000);
            assertEquals("SELECT dept, COUNT(*) FROM user WHERE id > 0 GROUP BY dept HAVING COUNT(*) > 5 ORDER BY id ASC LIMIT 1000", result);
        }
    }

    @Nested
    @DisplayName("lastId边界值")
    class LastIdBoundary {

        @Test
        @DisplayName("lastId为0（首次查询）")
        void shouldWorkWithZeroLastId() {
            String sql = "SELECT * FROM user WHERE status = 1";
            String result = interceptor.rebuildSql(sql, "id", 0, 1000);
            assertEquals("SELECT * FROM user WHERE status = 1 AND id > 0 ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("lastId为大数值")
        void shouldWorkWithLargeLastId() {
            String sql = "SELECT * FROM user";
            String result = interceptor.rebuildSql(sql, "id", 9999999L, 500);
            assertEquals("SELECT * FROM user WHERE id > 9999999 ORDER BY id ASC LIMIT 500", result);
        }
    }

    @Nested
    @DisplayName("大小写兼容")
    class CaseInsensitive {

        @Test
        @DisplayName("小写关键字")
        void shouldHandleLowerCase() {
            String sql = "select * from user where status = 1 order by id limit 10";
            String result = interceptor.rebuildSql(sql, "id", 100, 1000);
            assertEquals("SELECT * FROM user WHERE status = 1 AND id > 100 ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("混合大小写")
        void shouldHandleMixedCase() {
            String sql = "Select * From user Where status = 1 Order By id Limit 10";
            String result = interceptor.rebuildSql(sql, "id", 100, 1000);
            assertEquals("SELECT * FROM user WHERE status = 1 AND id > 100 ORDER BY id ASC LIMIT 1000", result);
        }
    }

    @Nested
    @DisplayName("被替换子句中的占位符计数")
    class PlaceholderCount {

        @Test
        @DisplayName("LIMIT 带占位符时计数为 1")
        void shouldCountLimitPlaceholder() {
            String sql = "SELECT * FROM user LIMIT ?";
            CursorPaginationInterceptor.RewriteResult r =
                    interceptor.rebuildSqlWithMeta(sql, new String[]{"id"}, new Object[]{0}, 1000);
            assertEquals(1, r.removedPlaceholders);
        }

        @Test
        @DisplayName("LIMIT + OFFSET 均带占位符时计数为 2")
        void shouldCountLimitAndOffsetPlaceholders() {
            String sql = "SELECT * FROM user LIMIT ? OFFSET ?";
            CursorPaginationInterceptor.RewriteResult r =
                    interceptor.rebuildSqlWithMeta(sql, new String[]{"id"}, new Object[]{0}, 1000);
            assertEquals(2, r.removedPlaceholders);
        }

        @Test
        @DisplayName("无 LIMIT/OFFSET 时计数为 0")
        void shouldCountZeroWhenNoLimitOffset() {
            String sql = "SELECT * FROM user WHERE status = 1";
            CursorPaginationInterceptor.RewriteResult r =
                    interceptor.rebuildSqlWithMeta(sql, new String[]{"id"}, new Object[]{0}, 1000);
            assertEquals(0, r.removedPlaceholders);
        }

        @Test
        @DisplayName("WHERE 子句中的占位符不被计入")
        void shouldNotCountWherePlaceholders() {
            String sql = "SELECT * FROM user WHERE status = ? AND age > ? LIMIT ?";
            CursorPaginationInterceptor.RewriteResult r =
                    interceptor.rebuildSqlWithMeta(sql, new String[]{"id"}, new Object[]{0}, 1000);
            assertEquals(1, r.removedPlaceholders);
        }
    }

    @Nested
    @DisplayName("多字段元组游标")
    class MultiFieldCursor {

        @Test
        @DisplayName("两字段元组比较 + 按顺序多列排序")
        void shouldGenerateTupleWhereAndMultiOrderBy() {
            String sql = "SELECT * FROM demo";
            String result = interceptor.rebuildSql(sql,
                    new String[]{"id", "name"},
                    new Object[]{10L, "user_10"},
                    20);
            assertEquals("SELECT * FROM demo WHERE (id, name) > (10, 'user_10') ORDER BY id ASC, name ASC LIMIT 20", result);
        }

        @Test
        @DisplayName("多字段 + 已有WHERE追加AND元组条件")
        void shouldAppendTupleConditionWithExistingWhere() {
            String sql = "SELECT * FROM demo WHERE status = 1";
            String result = interceptor.rebuildSql(sql,
                    new String[]{"create_time", "id"},
                    new Object[]{"2026-06-08 10:00:00", 42L},
                    100);
            assertEquals("SELECT * FROM demo WHERE status = 1 AND (create_time, id) > ('2026-06-08 10:00:00', 42) ORDER BY create_time ASC, id ASC LIMIT 100", result);
        }

        @Test
        @DisplayName("多字段驼峰名自动转下划线")
        void shouldNormalizeCamelCaseColumns() {
            String sql = "SELECT * FROM demo";
            String result = interceptor.rebuildSql(sql,
                    new String[]{"createTime", "idName"},
                    new Object[]{"2026-06-08", 99L},
                    50);
            assertEquals("SELECT * FROM demo WHERE (create_time, id_name) > ('2026-06-08', 99) ORDER BY create_time ASC, id_name ASC LIMIT 50", result);
        }

        @Test
        @DisplayName("多字段支持表别名前缀")
        void shouldSupportTableAliasInMultiFields() {
            String sql = "SELECT t.* FROM demo t WHERE t.status = 1";
            String result = interceptor.rebuildSql(sql,
                    new String[]{"t.create_time", "t.id"},
                    new Object[]{0L, 0L},
                    10);
            assertEquals("SELECT t.* FROM demo t WHERE t.status = 1 AND (t.create_time, t.id) > (0, 0) ORDER BY t.create_time ASC, t.id ASC LIMIT 10", result);
        }

        @Test
        @DisplayName("字符串包含单引号需转义")
        void shouldEscapeSingleQuoteInLiteral() {
            String sql = "SELECT * FROM demo";
            String result = interceptor.rebuildSql(sql,
                    new String[]{"id", "name"},
                    new Object[]{1L, "O'Brien"},
                    10);
            assertEquals("SELECT * FROM demo WHERE (id, name) > (1, 'O''Brien') ORDER BY id ASC, name ASC LIMIT 10", result);
        }

        @Test
        @DisplayName("单字段数组与原始单字段输出一致（向后兼容）")
        void shouldFallbackToScalarComparisonForSingleField() {
            String sql = "SELECT * FROM user WHERE status = 1";
            String result = interceptor.rebuildSql(sql,
                    new String[]{"id"}, new Object[]{500L}, 1000);
            assertEquals("SELECT * FROM user WHERE status = 1 AND id > 500 ORDER BY id ASC LIMIT 1000", result);
        }
    }

    @Nested
    @DisplayName("降序游标分页")
    class DescCursor {

        @Test
        @DisplayName("单字段降序：WHERE col < lastId ORDER BY col DESC")
        void shouldGenerateDescCursorCondition() {
            String sql = "SELECT * FROM user";
            String result = interceptor.rebuildSqlWithMeta(sql,
                    new String[]{"create_time"}, new Object[]{"2026-01-01"}, 1000, true, false).sql;
            assertEquals("SELECT * FROM user WHERE create_time < '2026-01-01' ORDER BY create_time DESC LIMIT 1000", result);
        }

        @Test
        @DisplayName("多字段降序：元组比较用 <，ORDER BY 全 DESC")
        void shouldGenerateDescTupleCondition() {
            String sql = "SELECT * FROM demo WHERE status = 1";
            String result = interceptor.rebuildSqlWithMeta(sql,
                    new String[]{"create_time", "id"},
                    new Object[]{"2026-01-01", 42L}, 100, true, false).sql;
            assertEquals("SELECT * FROM demo WHERE status = 1 AND (create_time, id) < ('2026-01-01', 42) ORDER BY create_time DESC, id DESC LIMIT 100", result);
        }

        @Test
        @DisplayName("降序 + 已有 WHERE 追加 AND")
        void shouldAppendAndConditionForDesc() {
            String sql = "SELECT * FROM user WHERE status = 1 ORDER BY create_time DESC";
            String result = interceptor.rebuildSqlWithMeta(sql,
                    new String[]{"create_time"}, new Object[]{"2026-06-08"}, 500, true, false).sql;
            assertEquals("SELECT * FROM user WHERE status = 1 AND create_time < '2026-06-08' ORDER BY create_time DESC LIMIT 500", result);
        }
    }

    @Nested
    @DisplayName("首次查询（lastIds 全为 null）")
    class FirstQuery {

        @Test
        @DisplayName("首次查询升序：不加 WHERE，只保留 ORDER BY ASC + LIMIT")
        void shouldSkipWhereOnFirstQueryAsc() {
            String sql = "SELECT * FROM user";
            String result = interceptor.rebuildSqlWithMeta(sql,
                    new String[]{"id"}, null, 1000, false, true).sql;
            assertEquals("SELECT * FROM user ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("首次查询降序：不加 WHERE，只保留 ORDER BY DESC + LIMIT")
        void shouldSkipWhereOnFirstQueryDesc() {
            String sql = "SELECT * FROM user";
            String result = interceptor.rebuildSqlWithMeta(sql,
                    new String[]{"create_time"}, null, 1000, true, true).sql;
            assertEquals("SELECT * FROM user ORDER BY create_time DESC LIMIT 1000", result);
        }

        @Test
        @DisplayName("首次查询 + 已有 WHERE：不加游标条件，只替换 ORDER BY 和 LIMIT")
        void shouldSkipCursorWhereWithExistingWhere() {
            String sql = "SELECT * FROM user WHERE status = 1 LIMIT 10";
            String result = interceptor.rebuildSqlWithMeta(sql,
                    new String[]{"id"}, null, 1000, false, true).sql;
            assertEquals("SELECT * FROM user WHERE status = 1 ORDER BY id ASC LIMIT 1000", result);
        }

        @Test
        @DisplayName("首次查询多字段：不加 WHERE，ORDER BY 全列 + LIMIT")
        void shouldSkipWhereOnFirstQueryMultiField() {
            String sql = "SELECT * FROM demo";
            String result = interceptor.rebuildSqlWithMeta(sql,
                    new String[]{"create_time", "id"}, null, 100, false, true).sql;
            assertEquals("SELECT * FROM demo ORDER BY create_time ASC, id ASC LIMIT 100", result);
        }
    }

}
