package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册请求 DTO
 */
@Data
@Schema(description = "注册请求参数")
public class RegisterRequest {
    
    @Schema(description = "用户邮箱", example = "user@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @Schema(description = "用户名", example = "newuser")
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @Schema(description = "用户密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "邮箱验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    private String code;
}
