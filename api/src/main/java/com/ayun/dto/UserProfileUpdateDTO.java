package com.ayun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户资料更新 DTO
 */
@Data
@Schema(description = "用户资料更新请求参数")
public class UserProfileUpdateDTO {

    @Schema(description = "用户名", example = "ayun")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在 2 到 20 个字符之间")
    private String username;

    @Schema(description = "个人简介", example = "资深后端开发专家")
    @Size(max = 200, message = "个人简介长度不能超过 200 个字符")
    private String bio;
}
