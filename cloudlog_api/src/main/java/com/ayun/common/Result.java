package com.ayun.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用响应结果类
 * @param <T> 数据泛型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用响应结果")
public class Result<T> {
    
    @Schema(description = "响应码", example = "200")
    private Integer code;
    
    @Schema(description = "响应消息", example = "操作成功")
    private String message;
    
    @Schema(description = "响应数据")
    private T data;

    /**
     * 成功返回结果
     * @param data 数据
     * @param <T> 数据类型
     * @return Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功返回结果（不带数据）
     * @param <T> 数据类型
     * @return Result对象
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败返回结果
     * @param code 错误码
     * @param message 错误信息
     * @param <T> 数据类型
     * @return Result对象
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
