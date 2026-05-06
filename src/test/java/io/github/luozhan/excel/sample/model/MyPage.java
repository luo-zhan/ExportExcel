package io.github.luozhan.excel.sample.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 自定义分页结果包装类
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MyPage<T> {
    private int pageNum;
    private int pageSize;
    private List<T> content;
}
