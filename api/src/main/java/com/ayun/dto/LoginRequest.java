package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO
 */
@Data
@Schema(description = "登录请求参数")
public class LoginRequest {
    
    @Schema(description = "用户邮箱", example = "admin@ayun.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @Schema(description = "用户密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    private String password;
}
