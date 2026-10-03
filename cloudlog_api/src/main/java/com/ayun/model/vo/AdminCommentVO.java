package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理后台评论视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理后台评论视图对象")
public class AdminCommentVO {

    @Schema(description = "评论ID")
    private Integer id;

    @Schema(description = "所属文章ID（雪花 ID）")
    private Long postId;

    @Schema(description = "所属文章标题（文章可能已删除）")
    private String postTitle;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "父评论ID，顶级评论为 0")
    private Integer parentId;

    @Schema(description = "父评论用户名，顶级评论为 null")
    private String parentUsername;

    @Schema(description = "父评论内容，顶级评论为 null")
    private String parentContent;

    @Schema(description = "审核状态: pending(待审核), approved(已通过), rejected(已拒绝)")
    private String status;

    @Schema(description = "机审命中的首个违禁词；关审核、未命中与该列上线前的存量评论均为 null", example = "广告")
    private String hitWord;

    @Schema(description = "评论时间")
    private LocalDateTime createdAt;

    @Schema(description = "评论者信息（匿名评论为 null）")
    private UserVO user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserVO {
        private Long id;
        private String username;
        private String avatar;
    }
}
