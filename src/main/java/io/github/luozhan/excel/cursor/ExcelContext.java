package io.github.luozhan.excel.cursor;

/**
 * 游标分页上下文（ThreadLocal）
 * <p>
 * 仅在Excel导出场景下激活，对普通分页查询无任何影响。
 * AOP设置上下文 → MyBatis拦截器读取上下文修改SQL → AOP更新lastId → 循环
 * <p>
 * 支持多字段复合游标：{@link CursorState#getDbColumns()} 长度&gt;1 时启用元组比较。
 *
 * @author luozhan
 * @since 2026-06-08
 */
public class ExcelContext {

    private static final ThreadLocal<Boolean> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<CursorState> CURSOR_CONTEXT = new ThreadLocal<>();

    public static void active() {
        CONTEXT.set(Boolean.TRUE);
    }

    public static boolean isActive() {
        return CONTEXT.get() != null && CONTEXT.get().equals(Boolean.TRUE);
    }

    /**
     * 激活游标分页上下文（多字段复合游标）。
     *
     * @param dbColumns    数据库列名数组（可包含表别名前缀），用于SQL改造，长度&gt;1 时使用元组比较
     * @param voFieldNames VO字段名数组，与 dbColumns 一一对应，用于从返回对象提取 lastId
     * @param batchSize    每批查询数量
     * @param voClass      导出VO类型，用于拦截器匹配目标SQL
     * @param entityClass  Mapper实际返回的实体类型（可选，DO→VO场景使用），{@code Void.class} 表示无实体映射
     */
    public static void activateCursorMode(String[] dbColumns, String[] voFieldNames, int batchSize,
                                          Class<?> voClass, Class<?> entityClass) {
        if (dbColumns == null || dbColumns.length == 0) {
            throw new IllegalArgumentException("dbColumns 不能为空");
        }
        if (voFieldNames == null || voFieldNames.length != dbColumns.length) {
            throw new IllegalArgumentException("voFieldNames 长度必须与 dbColumns 一致");
        }
        Object[] lastIds = new Object[dbColumns.length];
        CURSOR_CONTEXT.set(new CursorState(dbColumns, voFieldNames, batchSize, lastIds, voClass, entityClass));
    }

    /**
     * 获取当前上下文（未激活时返回null）
     */
    public static CursorState get() {
        return CURSOR_CONTEXT.get();
    }

    /**
     * 更新 lastIds（每批查询后由AOP调用）。
     * <p>支持多字段：传入数组长度必须与 {@link CursorState#getDbColumns()} 一致。
     */
    public static void updateLastIds(Object[] lastIds) {
        CursorState state = CURSOR_CONTEXT.get();
        if (state != null) {
            int expectedLen = state.dbColumns.length - state.startIndex;
            if (lastIds == null || lastIds.length != expectedLen) {
                throw new IllegalArgumentException("lastIds 长度必须为 dbColumns.length - startIndex = " + expectedLen);
            }
            state.setLastIds(lastIds);
        }
    }

    /**
     * 清除上下文（导出完成后必须调用）
     */
    public static void clear() {
        CURSOR_CONTEXT.remove();
        CONTEXT.remove();
    }

    /**
     * 判断游标分页是否激活
     */
    public static boolean isCursorModeActive() {
        return CURSOR_CONTEXT.get() != null;
    }

    /**
     * 游标分页状态
     */
    public static class CursorState {
        /**
         * 数据库列名数组（用于SQL改造），可包含表别名前缀；长度&gt;1 时启用元组比较
         */
        private final String[] dbColumns;
        /**
         * VO字段名数组（用于从返回对象提取lastId），与 {@link #dbColumns} 一一对应
         */
        private final String[] voFieldNames;
        private final int batchSize;
        private Object[] lastIds;
        /**
         * 导出VO类型，用于拦截器匹配目标SQL
         */
        private final Class<?> voClass;
        /**
         * Mapper实际返回的实体类型（DO→VO场景），{@code Void.class} 表示无实体映射
         */
        private final Class<?> entityClass;
        /**
         * ORDER BY 字段在 dbColumns 中的起始索引，默认 0。
         * 拦截器首次执行时根据原始 SQL 的 ORDER BY 解析后设置。
         */
        private int startIndex = 0;
        /**
         * 所有参与游标的字段的统一方向，true=DESC，false=ASC（默认）。
         */
        private boolean desc;

        public CursorState(String[] dbColumns, String[] voFieldNames, int batchSize, Object[] lastIds,
                           Class<?> voClass, Class<?> entityClass) {
            this.dbColumns = dbColumns;
            this.voFieldNames = voFieldNames;
            this.batchSize = batchSize;
            this.lastIds = lastIds;
            this.voClass = voClass;
            this.entityClass = entityClass;
        }

        public String[] getDbColumns() {
            return dbColumns;
        }

        public String[] getVoFieldNames() {
            return voFieldNames;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public Object[] getLastIds() {
            return lastIds;
        }

        public void setLastIds(Object[] lastIds) {
            this.lastIds = lastIds;
        }

        public Class<?> getVoClass() {
            return voClass;
        }

        public Class<?> getEntityClass() {
            return entityClass;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public void setStartIndex(int startIndex) {
            this.startIndex = startIndex;
        }

        public boolean isDesc() {
            return desc;
        }

        public void setDesc(boolean desc) {
            this.desc = desc;
        }
    }
}
