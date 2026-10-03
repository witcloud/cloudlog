package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理后台工作台概览视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理后台工作台概览")
public class DashboardVO {

    @Schema(description = "用户总数")
    private Long userCount;

    @Schema(description = "文章总数")
    private Long articleCount;

    @Schema(description = "待处理评论数")
    private Long pendingCommentCount;

    @Schema(description = "累计阅读量")
    private Long totalViews;

    @Schema(description = "最近文章")
    private List<AdminArticleVO> recentArticles;

    @Schema(description = "最近活动（最新评论）")
    private List<AdminCommentVO> recentComments;
}
