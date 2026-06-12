package io.github.luozhan.excel.cursor;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 游标分页拦截器
 * <p>
 * 仅在 {@link ExcelContext} 激活时（Excel导出场景）生效，对普通查询零影响。
 * 拦截 Executor.query（6 参数签名），在 SQL 进入 StatementHandler 前用 JSqlParser AST 改写，
 * 自动处理子查询、UNION、字符串字面量、注释等边界场景。
 * <p>
 * 之所以选择 Executor 级别拦截：MappedStatement 与 BoundSql 都是方法参数，
 * 可以直接通过 BoundSql 的公开构造函数创建新实例并替换 args[5]，
 * 完全避免反射改写 BoundSql.sql / parameterMappings 等私有字段。
 * <p>
 * SQL 改写逻辑：
 * <ol>
 *     <li>追加游标 WHERE 条件：单字段为 {@code col > lastId}；多字段为元组比较 {@code (col1, col2) > (v1, v2)}</li>
 *     <li>替换最外层 ORDER BY 为按全部游标列升序：{@code col1 ASC, col2 ASC, ...}</li>
 *     <li>替换最外层 LIMIT/OFFSET 为 {@code LIMIT batchSize}</li>
 * </ol>
 *
 * @author luozhan
 * @since 2026-06-08
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class CursorPaginationInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(CursorPaginationInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        ExcelContext.CursorState state = ExcelContext.get();
        if (state == null) {
            // 游标未激活，直接放行
            return invocation.proceed();
        }

        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        if (!shouldRewrite(ms, state)) {
            if (log.isDebugEnabled()) {
                log.debug("游标分页上下文激活，但当前 SQL 返回类型不匹配目标 VO/Entity，直接放行：{}", ms.getId());
            }
            return invocation.proceed();
        }

        BoundSql boundSql = ms.getBoundSql(parameter);
        String originalSql = boundSql.getSql();

        // 解析 ORDER BY 并设置 startIndex / desc（仅在首次执行时解析一次）
        boolean isFirstQuery = isAllNull(state.getLastIds());
        if (isFirstQuery) {
            OrderByInfo orderByInfo = parseOrderBy(originalSql, state.getDbColumns());
            state.setStartIndex(orderByInfo.startIndex);
            state.setDesc(orderByInfo.desc);
            if (log.isDebugEnabled()) {
                log.debug("游标分页ORDER BY解析完成：startIndex={}, desc={}, orderByCol={}",
                        orderByInfo.startIndex, orderByInfo.desc, orderByInfo.orderByCol);
            }
        }
        int startIndex = state.getStartIndex();
        boolean desc = state.isDesc();

        String[] effectiveDbColumns = Arrays.copyOfRange(state.getDbColumns(), startIndex, state.getDbColumns().length);
        Object[] lastIds = state.getLastIds();
        Object[] effectiveLastIds = isFirstQuery ? null : Arrays.copyOfRange(lastIds, startIndex, lastIds.length);

        // 过滤掉 null 字段：left join 场景下 tie-breaker 可能为 null，忽略该列的查询条件
        // 首次查询时不过滤，保持完整字段列表以生成 ORDER BY + LIMIT
        FilteredCursor cursor = isFirstQuery
                ? new FilteredCursor(effectiveDbColumns, null, false)
                : filterNonNullCursor(effectiveDbColumns, effectiveLastIds);

        RewriteResult result = rebuildSqlWithMeta(originalSql,
                cursor.dbColumns, cursor.lastIds,
                state.getBatchSize(), desc, !cursor.hasNonNull);

        if (!originalSql.equals(result.sql)) {
            BoundSql newBoundSql = buildNewBoundSql(ms, boundSql, result);
            // 用新 BoundSql 包装一个新的 MappedStatement，替换 args[0]，
            // 下游 CachingExecutor 调 ms.getBoundSql(...) / createCacheKey(...) 都将基于新 SQL
            args[0] = wrapMappedStatement(ms, newBoundSql);

            if (log.isDebugEnabled()) {
                String op = desc ? "<" : ">";
                log.debug("游标分页SQL改造完成，cursor=({}) {} ({}), batch={}",
                        String.join(",", cursor.dbColumns),
                        op,
                        joinLastIds(cursor.lastIds != null ? cursor.lastIds : new Object[0]),
                        state.getBatchSize());
                log.debug("原始SQL: {}", originalSql);
                log.debug("改造SQL: {}", result.sql);
            }
        }

        return invocation.proceed();
    }

    /**
     * 解析原始 SQL 的 ORDER BY，匹配到 {@code dbColumns} 中的索引，并返回方向。
     * <p>
     * 匹配规则（优先级）：
     * 1. 精确匹配（含表别名前缀）
     * 2. 去掉表别名前缀后匹配
     * 3. 下划线风格匹配（驼峰转下划线）
     */
    private OrderByInfo parseOrderBy(String sql, String[] dbColumns) {
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (!(stmt instanceof PlainSelect)) {
                return new OrderByInfo(0, false, null); // 无 ORDER BY，默认升序
            }
            PlainSelect plain = (PlainSelect) stmt;
            List<OrderByElement> orderByElements = plain.getOrderByElements();
            if (orderByElements == null || orderByElements.isEmpty()) {
                return new OrderByInfo(0, false, null); // 无 ORDER BY，默认升序
            }

            OrderByElement first = orderByElements.get(0);
            String orderByCol = first.getExpression().toString();
            // 去掉表别名前缀
            int dot = orderByCol.lastIndexOf('.');
            String orderByName = dot >= 0 ? orderByCol.substring(dot + 1) : orderByCol;
            boolean desc = !first.isAsc(); // JSqlParser: isAsc() 默认 true

            // 在 dbColumns 中匹配
            for (int i = 0; i < dbColumns.length; i++) {
                String col = dbColumns[i];
                String normalized = toSnakeCase(col);
                int colDot = normalized.lastIndexOf('.');
                String colName = colDot >= 0 ? normalized.substring(colDot + 1) : normalized;

                if (orderByName.equalsIgnoreCase(colName) ||
                        orderByName.equalsIgnoreCase(col)) {
                    return new OrderByInfo(i, desc, orderByCol);
                }
            }

            // 未匹配到
            log.error("ORDER BY 字段 '{}' 不在 @CursorField 列表 {} 中，游标分页无法确定排序方向", orderByName, Arrays.toString(dbColumns));
            throw new IllegalStateException("ORDER BY 字段 '" + orderByName + "' 未在 @CursorField 中配置");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析 ORDER BY 失败，使用默认升序: {}", e.getMessage());
            return new OrderByInfo(0, false, null);
        }
    }

    private static class OrderByInfo {
        final int startIndex;
        final boolean desc;
        final String orderByCol;

        OrderByInfo(int startIndex, boolean desc, String orderByCol) {
            this.startIndex = startIndex;
            this.desc = desc;
            this.orderByCol = orderByCol;
        }
    }

    /**
     * 过滤出非 null 的游标列及其对应的 lastId。
     */
    private FilteredCursor filterNonNullCursor(String[] effectiveDbColumns, Object[] effectiveLastIds) {
        List<String> nonNullDbCols = new ArrayList<>();
        List<Object> nonNullLastIds = new ArrayList<>();
        for (int i = 0; i < effectiveLastIds.length; i++) {
            if (effectiveLastIds[i] != null) {
                nonNullDbCols.add(effectiveDbColumns[i]);
                nonNullLastIds.add(effectiveLastIds[i]);
            }
        }
        return new FilteredCursor(
                nonNullDbCols.toArray(new String[0]),
                nonNullLastIds.toArray(new Object[0]),
                !nonNullDbCols.isEmpty()
        );
    }

    private static class FilteredCursor {
        final String[] dbColumns;
        final Object[] lastIds;
        final boolean hasNonNull;

        FilteredCursor(String[] dbColumns, Object[] lastIds, boolean hasNonNull) {
            this.dbColumns = dbColumns;
            this.lastIds = lastIds;
            this.hasNonNull = hasNonNull;
        }
    }

    /**
     * 判断数组是否全部为 null。
     */
    private boolean isAllNull(Object[] arr) {
        if (arr == null) {
            return true;
        }
        for (Object o : arr) {
            if (o != null) {
                return false;
            }
        }
        return true;
    }

    private String joinLastIds(Object[] lastIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lastIds.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(lastIds[i]);
        }
        return sb.toString();
    }

    /**
     * 基于改写结果构造新的 BoundSql。
     * <p>
     * 1) 通过公开构造函数直接传入新 SQL 与裁剪后的 parameterMappings；
     * 2) 拷贝原 BoundSql 上的 additionalParameter（动态 SQL foreach 等会注册）。
     */
    private BoundSql buildNewBoundSql(MappedStatement ms, BoundSql boundSql, RewriteResult result) {
        List<ParameterMapping> originalMappings = boundSql.getParameterMappings();
        if (originalMappings == null) {
            originalMappings = Collections.emptyList();
        }

        List<ParameterMapping> mappings;
        if (result.removedPlaceholders > 0 && originalMappings.size() >= result.removedPlaceholders) {
            int kept = originalMappings.size() - result.removedPlaceholders;
            mappings = new ArrayList<>(originalMappings.subList(0, kept));
        } else {
            mappings = new ArrayList<>(originalMappings);
        }

        BoundSql newBoundSql = new BoundSql(
                ms.getConfiguration(),
                result.sql,
                mappings,
                boundSql.getParameterObject());

        // 拷贝 additionalParameter（按原 mappings 中注册过的 property 复制）
        for (ParameterMapping pm : originalMappings) {
            String prop = pm.getProperty();
            if (boundSql.hasAdditionalParameter(prop)) {
                newBoundSql.setAdditionalParameter(prop, boundSql.getAdditionalParameter(prop));
            }
        }
        return newBoundSql;
    }

    /**
     * 用一个返回固定 BoundSql 的 SqlSource 包装出新的 MappedStatement。
     * <p>
     * 这里复制原 ms 的全部元信息（resultMaps/cache/keyGenerator/lang 等），
     * 仅替换 SqlSource，使下游 {@code ms.getBoundSql(parameter)} 返回我们改写后的 BoundSql。
     */
    private MappedStatement wrapMappedStatement(MappedStatement ms, BoundSql newBoundSql) {
        SqlSource newSqlSource = new BoundSqlSqlSource(newBoundSql);
        MappedStatement.Builder builder = new MappedStatement.Builder(
                ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length > 0) {
            builder.keyProperty(String.join(",", ms.getKeyProperties()));
        }
        if (ms.getKeyColumns() != null && ms.getKeyColumns().length > 0) {
            builder.keyColumn(String.join(",", ms.getKeyColumns()));
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        builder.resultOrdered(ms.isResultOrdered());
        builder.lang(ms.getLang());
        builder.databaseId(ms.getDatabaseId());
        if (ms.getResultSets() != null && ms.getResultSets().length > 0) {
            builder.resultSets(String.join(",", ms.getResultSets()));
        }
        return builder.build();
    }

    /**
     * 包装一个固定 BoundSql 的 SqlSource，无视入参始终返回同一个 BoundSql。
     */
    private static final class BoundSqlSqlSource implements SqlSource {
        private final BoundSql boundSql;

        BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }

    /**
     * 判断当前 MappedStatement 是否为本次游标导出需要改写的目标 SQL。
     * <p>
     * 匹配规则（满足其一即可）：
     * 1. MappedStatement 的返回类型等于 {@link ExcelContext.CursorState#getVoClass()}
     * 2. MappedStatement 的返回类型等于 {@link ExcelContext.CursorState#getEntityClass()}
     */
    private boolean shouldRewrite(MappedStatement ms, ExcelContext.CursorState state) {
        Class<?> resultType = extractResultType(ms);
        if (resultType == null || state == null) {
            return false;
        }
        Class<?> voClass = state.getVoClass();
        if (voClass != null && voClass.equals(resultType)) {
            return true;
        }
        Class<?> entityClass = state.getEntityClass();
        return entityClass != null
                && !entityClass.equals(Void.class)
                && entityClass.equals(resultType);
    }

    /**
     * 提取 MappedStatement 的主返回类型。优先取第一个 ResultMap 的 type。
     */
    private Class<?> extractResultType(MappedStatement ms) {
        List<ResultMap> resultMaps = ms.getResultMaps();
        if (resultMaps != null && !resultMaps.isEmpty()) {
            return resultMaps.get(0).getType();
        }
        return null;
    }

    /**
     * 兼容旧签名的 SQL 改造入口，默认 ASC 方向、非首次查询。
     */
    RewriteResult rebuildSqlWithMeta(String sql, String[] dbColumns, Object[] lastIds, int batchSize) {
        boolean desc = false;
        boolean isFirstQuery = isAllNull(lastIds);
        return rebuildSqlWithMeta(sql, dbColumns, lastIds, batchSize, desc, isFirstQuery);
    }

    /**
     * 改造 SQL 并返回元数据（包含被替换子句中的 ? 占位符数量），
     * 供拦截器在重建 BoundSql 时同步裁剪 parameterMappings。
     *
     * @param sql          原始 SQL
     * @param dbColumns    有效游标字段数组（已按 startIndex 截取）
     * @param lastIds      有效 lastIds 数组；首次查询时为 null
     * @param batchSize    每批数量
     * @param desc         true=降序，false=升序
     * @param isFirstQuery 首次查询（lastIds 全为 null）时不生成 WHERE 条件
     */
    RewriteResult rebuildSqlWithMeta(String sql, String[] dbColumns, Object[] lastIds,
                                     int batchSize, boolean desc, boolean isFirstQuery) {
        if (dbColumns == null || dbColumns.length == 0) {
            return new RewriteResult(sql, 0);
        }
        if (!isFirstQuery && (lastIds == null || lastIds.length != dbColumns.length)) {
            throw new IllegalArgumentException("lastIds 长度必须与 dbColumns 一致");
        }
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            log.warn("游标分页SQL解析失败，回退原SQL: {}", e.getMessage());
            return new RewriteResult(sql, 0);
        }
        if (!(stmt instanceof Select)) {
            return new RewriteResult(sql, 0);
        }
        Select body = ((Select) stmt);
        if (!(body instanceof PlainSelect)) {
            return new RewriteResult(sql, 0);
        }
        PlainSelect plain = (PlainSelect) body;

        // 在替换前统计即将被丢弃子句中的占位符数量（基于 AST，无需扫描整段 SQL）
        int removedPlaceholders = countPlaceholdersInReplacedClauses(plain);

        // 1) 追加游标 WHERE 条件（首次查询跳过）
        if (!isFirstQuery) {
            Expression cursorCond = buildCursorCondition(dbColumns, lastIds, desc);
            Expression where = plain.getWhere();
            plain.setWhere(where == null ? cursorCond : new AndExpression(where, cursorCond));
        }

        // 2) 替换最外层 ORDER BY 为所有游标列按统一方向排序
        List<OrderByElement> orderBy = new ArrayList<>(dbColumns.length);
        for (String col : dbColumns) {
            OrderByElement ob = new OrderByElement();
            ob.setExpression(parseColumn(col));
            ob.setAsc(!desc);
            ob.setAscDescPresent(true);
            orderBy.add(ob);
        }
        plain.setOrderByElements(orderBy);

        // 3) 替换最外层 LIMIT/OFFSET
        Limit limit = new Limit();
        limit.setRowCount(new LongValue(batchSize));
        plain.setLimit(limit);
        plain.setOffset(null);

        return new RewriteResult(body.toString(), removedPlaceholders);
    }

    /**
     * 构造游标 WHERE 表达式：
     * <ul>
     *     <li>单字段：直接 AST 构造 {@code col > literal} 或 {@code col < literal}</li>
     *     <li>多字段：拼接元组字符串后交给 JSqlParser 解析，避免不同 JSqlParser 版本 RowConstructor API 差异</li>
     * </ul>
     *
     * @param desc true=降序（使用 <），false=升序（使用 >）
     */
    private Expression buildCursorCondition(String[] dbColumns, Object[] lastIds, boolean desc) {
        if (dbColumns.length == 1) {
            if (desc) {
                MinorThan cond = new MinorThan();
                cond.setLeftExpression(parseColumn(dbColumns[0]));
                cond.setRightExpression(toLiteral(lastIds[0]));
                return cond;
            } else {
                GreaterThan cond = new GreaterThan();
                cond.setLeftExpression(parseColumn(dbColumns[0]));
                cond.setRightExpression(toLiteral(lastIds[0]));
                return cond;
            }
        }
        StringBuilder cols = new StringBuilder("(");
        StringBuilder vals = new StringBuilder("(");
        for (int i = 0; i < dbColumns.length; i++) {
            if (i > 0) {
                cols.append(", ");
                vals.append(", ");
            }
            cols.append(toSnakeCase(dbColumns[i]));
            vals.append(toLiteralString(lastIds[i]));
        }
        cols.append(")");
        vals.append(")");
        String op = desc ? " < " : " > ";
        String tuple = cols + op + vals;
        try {
            return CCJSqlParserUtil.parseCondExpression(tuple);
        } catch (Exception e) {
            throw new IllegalStateException("元组游标条件解析失败：" + tuple, e);
        }
    }

    /**
     * 统计即将被替换的 ORDER BY / LIMIT / OFFSET 子句中的 JdbcParameter(?) 数量。
     * <p>
     * 利用 JSqlParser AST 节点类型直接判断，相比对全量 SQL 字符扫描更精确，
     * 也无需关心字符串字面量、注释等边界场景。
     * <p>
     * 标准游标流程下 MyBatis-Plus 分页插件已被禁用（IPage.size = -1），通常返回 0；
     * 仅当用户在 Mapper XML 中手写了 LIMIT #{size}、OFFSET #{offset}
     * 等占位符时，才会有正值需要裁剪。
     */
    private int countPlaceholdersInReplacedClauses(PlainSelect plain) {
        int count = 0;

        Limit limit = plain.getLimit();
        if (limit != null) {
            if (limit.getRowCount() instanceof JdbcParameter) {
                count++;
            }
            // MySQL 风格 LIMIT offset, rowCount
            if (limit.getOffset() instanceof JdbcParameter) {
                count++;
            }
        }

        Offset offset = plain.getOffset();
        if (offset != null && offset.getOffset() instanceof JdbcParameter) {
            count++;
        }

        List<OrderByElement> orderBy = plain.getOrderByElements();
        if (orderBy != null) {
            for (OrderByElement elem : orderBy) {
                if (elem.getExpression() instanceof JdbcParameter) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * 解析游标字段名，支持表别名前缀（如 "t.id"）。
     * 同时将驼峰命名（idName）自动转换为数据库列名风格的下划线命名（id_name）。
     */
    private Column parseColumn(String name) {
        return new Column(toSnakeCase(name));
    }

    /**
     * 驼峰转下划线，保留表别名前缀（如 "t.idName" -&gt; "t.id_name"）。
     * 字符遍历实现，零正则、零额外对象分配热点；
     * 连续大写不拆分（如 "userID" -&gt; "user_id"、"URLPath" -&gt; "urlpath"）。
     */
    private String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String prefix = dot >= 0 ? name.substring(0, dot + 1) : "";
        String column = dot >= 0 ? name.substring(dot + 1) : name;

        StringBuilder sb = new StringBuilder(column.length() + 4);
        for (int i = 0; i < column.length(); i++) {
            char c = column.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    char prev = column.charAt(i - 1);
                    if (Character.isLowerCase(prev) || Character.isDigit(prev)) {
                        sb.append('_');
                    }
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return prefix + sb;
    }

    /**
     * lastId 字面量序列化：数字直出，布尔转 1/0，其他类型用单引号字符串包装并转义单引号。
     */
    private String literalSql(Object v) {
        if (v == null) {
            return "0";
        }
        if (v instanceof Number) {
            return v.toString();
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? "1" : "0";
        }
        return "'" + v.toString().replace("'", "''") + "'";
    }

    /**
     * lastId 字面量序列化为 JSqlParser Expression。
     */
    private Expression toLiteral(Object v) {
        try {
            return CCJSqlParserUtil.parseExpression(literalSql(v));
        } catch (Exception e) {
            throw new IllegalStateException("游标字面量解析失败：" + v, e);
        }
    }

    /**
     * lastId 字面量字符串化（用于元组拼接）。
     */
    private String toLiteralString(Object v) {
        return literalSql(v);
    }

    /**
     * SQL 改写结果：包含改写后的 SQL，以及被替换子句中原本存在的占位符数量。
     */
    static class RewriteResult {
        final String sql;
        final int removedPlaceholders;

        RewriteResult(String sql, int removedPlaceholders) {
            this.sql = sql;
            this.removedPlaceholders = removedPlaceholders;
        }
    }
}
