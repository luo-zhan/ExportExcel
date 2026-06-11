package io.github.luozhan.excel.cursor;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 游标元数据解析器
 * <p>
 * 负责扫描 VO 类上的 {@link CursorField} 字段注解与 {@link CursorEntity} 类注解，
 * 构造 {@link CursorMetadata}。每个 VO Class 仅扫描一次，结果缓存供后续复用。
 *
 * @author luozhan
 * @since 2026-06-10
 */
public class CursorMetadataResolver {

    /**
     * key = VO Class，value = 解析结果（Optional.empty 表示该 VO 未启用游标分页）
     */
    private final ConcurrentMap<Class<?>, Optional<CursorMetadata>> cache = new ConcurrentHashMap<>();

    /**
     * 解析 VO 类的游标元数据。
     * <ul>
     *     <li>未标注任何 {@link CursorField}：返回 null</li>
     *     <li>多个字段标注：按 {@link CursorField#order()} 升序排列；order 重复时报错</li>
     *     <li>{@link CursorEntity#value()}：从 VO 类上读取，未声明时为 {@link Void}</li>
     * </ul>
     *
     * @param voClass 导出 VO 的 Class
     * @return 解析结果，未启用游标分页时返回 null
     */
    public CursorMetadata resolve(Class<?> voClass) {
        return cache.computeIfAbsent(voClass, this::doResolve).orElse(null);
    }

    private Optional<CursorMetadata> doResolve(Class<?> clazz) {
        List<FieldEntry> entries = collectAnnotatedFields(clazz);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        if (entries.size() > 1) {
            checkOrderUnique(clazz, entries);
        }
        entries.sort(Comparator.comparingInt(e -> e.order));

        Field[] voFields = new Field[entries.size()];
        String[] dbColumns = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            voFields[i] = entries.get(i).field;
            dbColumns[i] = entries.get(i).dbColumn;
        }
        return Optional.of(new CursorMetadata(voFields, dbColumns, resolveEntityClass(clazz)));
    }

    /**
     * 沿继承链收集所有标注了 {@link CursorField} 的字段
     */
    private List<FieldEntry> collectAnnotatedFields(Class<?> clazz) {
        List<FieldEntry> entries = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                CursorField cf = f.getAnnotation(CursorField.class);
                if (cf == null) {
                    continue;
                }
                f.setAccessible(true);
                String value = cf.value();
                String dbColumn = (value == null || value.isEmpty()) ? f.getName() : value;
                entries.add(new FieldEntry(cf.order(), f, dbColumn));
            }
            current = current.getSuperclass();
        }
        return entries;
    }

    private void checkOrderUnique(Class<?> clazz, List<FieldEntry> entries) {
        Set<Integer> seen = new HashSet<>();
        for (FieldEntry entry : entries) {
            if (!seen.add(entry.order)) {
                throw new IllegalArgumentException("类 " + clazz.getSimpleName()
                        + " 中多个 @CursorField 的 order=" + entry.order
                        + " 重复，请为每个字段设置不同的 order");
            }
        }
    }

    private Class<?> resolveEntityClass(Class<?> clazz) {
        CursorEntity ce = clazz.getAnnotation(CursorEntity.class);
        return ce != null ? ce.value() : Void.class;
    }

    /**
     * 解析阶段的临时条目，最终按 order 排序后转换为 {@link CursorMetadata}
     */
    private static final class FieldEntry {
        final int order;
        final Field field;
        final String dbColumn;

        FieldEntry(int order, Field field, String dbColumn) {
            this.order = order;
            this.field = field;
            this.dbColumn = dbColumn;
        }
    }
}
