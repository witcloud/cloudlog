package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关注关系列表用的用户简要视图：不含邮箱、角色与状态列
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户简要视图")
public class UserBriefVO {

    @Schema(description = "用户ID（雪花 ID）")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "个人简介")
    private String bio;

    @Schema(description = "职业")
    private String occupation;

    @Schema(description = "当前浏览者是否已关注此人；未登录与个人主页列表不标注，为 null")
    private Boolean followed;

    @Schema(description = "此人是否已关注当前浏览者，与 followed 同时为真即互相关注；未登录与个人主页列表不标注，为 null")
    private Boolean followsMe;
}
