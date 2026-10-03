package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求 DTO
 */
@Data
@Schema(description = "重置密码请求参数")
public class PasswordResetRequest {

    @Schema(description = "账号邮箱", example = "user@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "邮箱收到的验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    private String code;

    @Schema(description = "新密码", example = "ayun2026")
    @NotBlank(message = "新密码不能为空")
    // 与 RegisterRequest 同档：8 是口令强度下限，72 是 BCrypt 的截断边界
    @Size(min = 8, max = 72, message = "密码长度需在 8-72 位之间")
    private String newPassword;
}
