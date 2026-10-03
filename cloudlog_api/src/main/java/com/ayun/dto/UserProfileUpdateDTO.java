package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户资料更新 DTO
 */
@Data
@Schema(description = "用户资料更新请求参数")
public class UserProfileUpdateDTO {

    // 用户名与注册侧同规则（见 UsernameRules）：同一个字段两套口径会让「注册起不了的名、改资料时改得动」
    @Schema(description = "用户名", example = "ayun")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度需在 2-30 位之间")
    @Pattern(regexp = UsernameRules.PATTERN, message = UsernameRules.MESSAGE)
    private String username;

    @Schema(description = "个人简介", example = "资深后端开发专家")
    @Size(max = 200, message = "个人简介长度不能超过 200 个字符")
    private String bio;

    @Schema(description = "职业", example = "后端工程师")
    @Size(max = 30, message = "职业长度不能超过 30 个字符")
    private String occupation;
}
