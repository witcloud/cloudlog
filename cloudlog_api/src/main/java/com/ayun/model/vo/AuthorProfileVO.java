package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 他人作者主页视图：用户简要四格加公开统计，不含邮箱、角色与状态列
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "作者主页公开视图")
public class AuthorProfileVO {

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

    @Schema(description = "该作者被关注数")
    private Integer followers;

    @Schema(description = "该作者关注的人数")
    private Integer following;

    @Schema(description = "当前浏览者是否已关注该作者；未登录为 null，作者本人恒为 false")
    private Boolean followed;

    @Schema(description = "该作者已发布文章数")
    private Integer articleCount;

    @Schema(description = "该作者全部已发布文章的获赞总数")
    private Integer likeCount;

    @Schema(description = "该作者全部已发布文章的阅读总量")
    private Integer viewCount;
}
