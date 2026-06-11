package io.github.luozhan.excel.cursor;

import java.lang.reflect.Field;

/**
 * 游标分页元数据
 * <p>
 * 描述一个 VO 类启用游标分页所需的全部静态信息：
 * <ul>
 *     <li>{@link #dbColumns()}：数据库列名数组（按 {@link CursorField#order()} 升序）</li>
 *     <li>{@link #voFieldNames()}：与 dbColumns 一一对应的 VO 字段名</li>
 *     <li>{@link #entityClass()}：来自类级 {@link CursorEntity}，未声明时为 {@link Void}</li>
 * </ul>
 * 同时封装从 VO 记录中按顺序提取游标值的能力（{@link #extractLastIds(Object)}），
 * 使切面层只需关心导出循环的编排，无需直接操作反射字段。
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
     * @param record 上一批的最后一条记录（不可为 null）
     * @return 与 {@link #dbColumns()} 等长的游标值数组，任一字段为 null 时抛出异常
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
     * @param record     上一批的最后一条记录（不可为 null）
     * @param startIndex 起始索引（0-based），从该索引开始到末尾的字段参与提取
     * @return 游标值数组，任一字段为 null 时抛出异常
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
                if (value == null) {
                    throw new IllegalArgumentException("游标字段 '" + field.getName() + "' 的值为null，无法用于游标分页");
                }
                lastIds[i] = value;
            } catch (IllegalAccessException e) {
                throw new RuntimeException("读取游标字段 '" + field.getName() + "' 失败", e);
            }
        }
        return lastIds;
    }
}
