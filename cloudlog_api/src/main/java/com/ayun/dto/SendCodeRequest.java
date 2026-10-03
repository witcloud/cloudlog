package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送邮箱验证码的请求体。注册与找回密码共用这一份邮箱入参规则：
 * 发码是有代价的动作（一封真邮件 + 一次频控占用），邮箱格式必须在落到这两件事之前就先判掉
 */
@Data
@Schema(description = "发送邮箱验证码参数")
public class SendCodeRequest {

    @Schema(description = "用户邮箱", example = "user@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    // 上限与 users.email 列宽一致，超长在发码前就拒掉，免得给一个根本写不进库的地址发一封邮件
    @Size(max = 255, message = "邮箱长度不能超过 255 个字符")
    private String email;
}
