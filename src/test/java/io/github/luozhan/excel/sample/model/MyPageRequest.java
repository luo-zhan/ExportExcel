package io.github.luozhan.excel.sample.model;

import lombok.*;

/**
 * 示例项目中的自定义分页请求参数。
 * <p>
 * 用于演示“项目自有分页参数类型 → PageParamHandler”的接入方式，
 * 参见 {@link io.github.luozhan.excel.sample.SampleApplication#myPageParamHandler()}。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MyPageRequest {

    /**
     * 当前页码（1-based）
     */
    private int pageNum;

    /** 每页记录数 */
    private int pageSize;
}
