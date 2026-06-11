package io.github.luozhan.excel.sample.model;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 集成测试专用的 {@link IPage} 实现。
 * <p>
 * MyBatis-Plus 提供的 {@code Page} 带有复杂的分页计数逻辑，
 * 本类仅覆盖 {@link IPage} 必需的字段与抽象方法，提供无参构造
 * 以便 Spring MVC 参数绑定。
 *
 * @param <T> 分页记录类型
 */
public class TestPage<T> implements IPage<T> {

    private List<T> records = new ArrayList<>();
    private long total = 0L;
    private long size = 10L;
    private long current = 1L;

    public TestPage() {
    }

    @Override
    public List<T> getRecords() {
        return records;
    }

    @Override
    public IPage<T> setRecords(List<T> records) {
        this.records = records;
        return this;
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public IPage<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public IPage<T> setSize(long size) {
        this.size = size;
        return this;
    }

    @Override
    public long getCurrent() {
        return current;
    }

    @Override
    public IPage<T> setCurrent(long current) {
        this.current = current;
        return this;
    }

    @Override
    public List<OrderItem> orders() {
        return Collections.emptyList();
    }
}
