package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理后台用户视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理后台用户视图对象")
public class AdminUserVO {

    @Schema(description = "用户ID（雪花 ID）")
    private Long id;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "个人简介")
    private String bio;

    @Schema(description = "职业")
    private String occupation;

    @Schema(description = "角色: admin(管理员), user(普通用户)", example = "user")
    private String role;

    @Schema(description = "状态：1-正常, 0-禁用", example = "1")
    private Integer status;

    @Schema(description = "文章数量")
    private Integer articleCount;

    @Schema(description = "上次登录时间，从未登录过为 null")
    private LocalDateTime lastLoginAt;

    @Schema(description = "上次登录 IP，仅在后台列表下发，从未登录过为 null")
    private String lastLoginIp;

    @Schema(description = "注册时间")
    private LocalDateTime createdAt;
}
