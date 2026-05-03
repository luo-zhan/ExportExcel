package io.github.luozhan.excel.sample.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 自定义分页结果包装类
 */
@Getter
@Setter
@AllArgsConstructor
@ToString
public class MyPage<T> {
    int pageNum;
    int pageSize;
    List<T> content;
}
