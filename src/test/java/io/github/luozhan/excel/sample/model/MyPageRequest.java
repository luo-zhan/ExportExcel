package io.github.luozhan.excel.sample.model;

import lombok.*;

/**
 * 自定义分页请求参数
 */
@Getter
@Setter
@AllArgsConstructor
@ToString
public class MyPageRequest {
    int pageNum;
        int pageSize;
}
