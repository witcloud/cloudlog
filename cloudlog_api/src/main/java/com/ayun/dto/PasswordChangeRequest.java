package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO
 */
@Data
@Schema(description = "修改密码请求参数")
public class PasswordChangeRequest {

    @Schema(description = "当前密码")
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码", example = "ayun2026")
    @NotBlank(message = "新密码不能为空")
    // 与 RegisterRequest 同档：8 是口令强度下限，72 是 BCrypt 的截断边界
    @Size(min = 8, max = 72, message = "密码长度需在 8-72 位之间")
    private String newPassword;
}
