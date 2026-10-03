package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    // 上限与 users.email 列宽一致，超长在写库前就拒掉，免得撞到 MySQL 截断
    @Size(max = 255, message = "邮箱长度不能超过 255 个字符")
    private String email;
    
    @Schema(description = "用户名", example = "newuser")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度需在 2-30 位之间")
    @Pattern(regexp = UsernameRules.PATTERN, message = UsernameRules.MESSAGE)
    private String username;
    
    @Schema(description = "用户密码", example = "ayun2026")
    @NotBlank(message = "密码不能为空")
    // 72 是 BCrypt 的截断边界：超出部分不参与哈希，放到校验里是为了不让用户以为第 73 位起也受保护
    @Size(min = 8, max = 72, message = "密码长度需在 8-72 位之间")
    private String password;

    @Schema(description = "邮箱验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    private String code;
}
