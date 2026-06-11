package io.github.luozhan.excel.sample.model;

import lombok.Data;

/**
 * 示例项目中的统一响应体包装。
 * <p>
 * 用于演示“项目自有响应体 → List 转换器”的接入方式，
 * 参见 {@link io.github.luozhan.excel.sample.SampleApplication#resultToListConverter()}。
 *
 * @param <T> 响应数据类型
 * @author luozhan
 * @since 2026/5/3
 */
@Data
public class Result<T> {

    /**
     * 响应码：200 成功，500 失败，401 未授权等
     */
    private Integer code;

    /** 响应提示信息 */
    private String msg;

    /** 响应业务数据 */
    private T data;

    /**
     * 构造一个表示成功的 {@link Result}。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        return result;
    }
}
