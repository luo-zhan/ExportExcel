package io.github.luozhan.excel.cursor;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 游标分页元数据
 * <p>
 * 描述一个 VO 类启用游标分页所需的全部静态信息：
 * <ul>
 *     <li>{@link #dbColumns()}：数据库列名数组（按 {@link CursorField#order()} 排序）</li>
 *     <li>{@link #voFieldNames()}：与 dbColumns 一一对应的 VO 字段名</li>
 *     <li>{@link #entityClass()}：来自类级 {@link CursorEntity}，通过该类和sql的ResultType匹配，以判断是否为需要改写的sol</li>
 * </ul>
 *
 * @author luozhan
 * @since 2026-06-10
 */
public final class CursorMetadata {

    private final Field[] voFields;
    private final String[] dbColumns;
    private final String[] voFieldNames;
    private final Class<?> entityClass;

    public CursorMetadata(Field[] voFields, String[] dbColumns, Class<?> entityClass) {
        this.voFields = voFields;
        this.dbColumns = dbColumns;
        this.entityClass = entityClass;
        this.voFieldNames = new String[voFields.length];
        for (int i = 0; i < voFields.length; i++) {
            this.voFieldNames[i] = voFields[i].getName();
        }
    }

    public String[] dbColumns() {
        return dbColumns;
    }

    public String[] voFieldNames() {
        return voFieldNames;
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public int size() {
        return voFields.length;
    }

    /**
     * 从一条 VO 记录中按 {@link CursorField#order()} 顺序提取所有游标字段的值，
     * 用作下一批查询的 lastIds（元组比较的右值）。
     *
     * @param record 上一批的最后一条记录】
     * @return 与 {@link #dbColumns()} 等长的游标值数组，允许 null 值
     */
    public Object[] extractLastIds(Object record) {
        return extractLastIds(record, 0);
    }

    /**
     * 从一条 VO 记录中按 {@link CursorField#order()} 顺序、从指定起始索引开始提取游标字段的值。
     * <p>
     * 当拦截器根据原始 SQL 的 ORDER BY 解析出有效字段起始索引后使用，
     * 返回的数组长度 = {@link #size()} - startIndex。
     *
     * @param record     上一批的最后一条记录】
     * @param startIndex 起始索引（0-based），从该索引开始到末尾的字段参与提取
     * @return 游标值数组，允许 null 值
     */
    public Object[] extractLastIds(Object record, int startIndex) {
        if (startIndex < 0 || startIndex >= voFields.length) {
            throw new IllegalArgumentException("startIndex 越界: " + startIndex + ", 总字段数: " + voFields.length);
        }
        int len = voFields.length - startIndex;
        Object[] lastIds = new Object[len];
        for (int i = 0; i < len; i++) {
            Field field = voFields[startIndex + i];
            try {
                Object value = field.get(record);
                lastIds[i] = value;
            } catch (IllegalAccessException e) {
                throw new RuntimeException("读取游标字段 '" + field.getName() + "' 失败", e);
            }
        }
        return lastIds;
    }

    /**
     * 从一批 VO 记录中提取各字段最后一条非空值。
     * <p>
     * 每个字段独立地从后向前搜索，找到该字段在批次中最后一条非 null 记录的值。
     * left join 场景下，右表无匹配时 tie-breaker 字段可能为 null，
     * 此时该字段取前面记录的非 null 值，而非整体跳过该批次。
     *
     * @param data       当前批次数据
     * @param startIndex 起始索引（来自 CursorState）
     * @return 游标值数组，任一字段在当前批次全为 null 时对应位置为 null
     */
    public Object[] extractLastIdsFromBatch(List<?> data, int startIndex) {
        if (startIndex < 0 || startIndex >= voFields.length) {
            throw new IllegalArgumentException("startIndex 越界: " + startIndex + ", 总字段数: " + voFields.length);
        }
        int len = voFields.length - startIndex;
        Object[] lastIds = new Object[len];
        for (int fieldIdx = 0; fieldIdx < len; fieldIdx++) {
            Field field = voFields[startIndex + fieldIdx];
            // 从后向前搜索该字段的非 null 值
            for (int i = data.size() - 1; i >= 0; i--) {
                try {
                    Object value = field.get(data.get(i));
                    if (value != null) {
                        lastIds[fieldIdx] = value;
                        break;
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("读取游标字段 '" + field.getName() + "' 失败", e);
                }
            }
        }
        return lastIds;
    }
}
