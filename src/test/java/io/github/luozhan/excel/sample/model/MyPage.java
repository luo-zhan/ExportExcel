package io.github.luozhan.excel.sample.model;

import lombok.*;

import java.util.List;

/**
 * 示例项目中的自定义分页响应体。
 * <p>
 * 用于演示“项目自有分页类型 → List 转换器”的接入方式，
 * 参见 {@link io.github.luozhan.excel.sample.SampleApplication#pageToListConverter()}。
 *
 * @param <T> 分页记录类型
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MyPage<T> {

    /**
     * 当前页码（1-based）
     */
    private int pageNum;

    /** 每页记录数 */
    private int pageSize;

    /** 当前页记录集合 */
    private List<T> content;
}
