package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改邮箱请求 DTO
 */
@Data
@Schema(description = "修改邮箱请求参数")
public class EmailChangeRequest {

    @Schema(description = "新邮箱", example = "new.reader@example.com")
    @NotBlank(message = "新邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String newEmail;

    @Schema(description = "新邮箱收到的验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    private String code;

    @Schema(description = "当前密码")
    @NotBlank(message = "当前密码不能为空")
    private String password;
}
