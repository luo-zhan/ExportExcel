package io.github.luozhan.excel.sample.model;

import lombok.Data;

/**
 * 统一包装类
 *
 * @author luozhan
 * @since 2026/5/3
 */
@Data
public class Result<T> {
    /**
     * 响应码：200成功，500失败，401未授权等
     */
    private Integer code;

    /**
     * 响应信息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

}
