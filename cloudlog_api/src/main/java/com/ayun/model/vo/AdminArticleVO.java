package com.ayun.model.vo;

import com.ayun.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台文章列表视图对象（不含正文，便于列表展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理后台文章视图对象")
public class AdminArticleVO {

    @Schema(description = "文章ID（雪花 ID）")
    private Long id;

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "状态: draft(草稿), published(已发布), scheduled(定时发布), offline(已下线)")
    private String status;

    @Schema(description = "是否置顶")
    private Integer isTop;

    @Schema(description = "置顶排序权重，越大越靠前；仅 isTop=1 时参与排序")
    private Integer topOrder;

    @Schema(description = "最近一次下线的时刻；重新上线不清空，从未下线过为 null")
    private LocalDateTime offlinedAt;

    @Schema(description = "最近一次执行下线的操作者用户名；仅后台列表查询填充")
    private String offlinedByName;

    @Schema(description = "阅读量")
    private Integer views;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "作者信息")
    private PostVO.AuthorVO author;

    @Schema(description = "分类信息")
    private PostVO.CategoryVO category;

    public static AdminArticleVO from(Post post) {
        PostVO.AuthorVO author = null;
        if (post.getAuthor() != null) {
            author = PostVO.AuthorVO.builder()
                    .id(post.getAuthor().getId())
                    .username(post.getAuthor().getUsername())
                    .avatar(post.getAuthor().getAvatar())
                    .build();
        }
        PostVO.CategoryVO category = null;
        if (post.getCategory() != null) {
            category = PostVO.CategoryVO.builder()
                    .id(post.getCategory().getId())
                    .name(post.getCategory().getName())
                    .build();
        }
        return AdminArticleVO.builder()
                .id(post.getId())
                .title(post.getTitle())
                .status(post.getStatus())
                .isTop(post.getIsTop())
                .topOrder(post.getTopOrder())
                .offlinedAt(post.getOfflinedAt())
                .offlinedByName(post.getOfflinedByUser() == null ? null : post.getOfflinedByUser().getUsername())
                .views(post.getViewCount())
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .author(author)
                .category(category)
                .build();
    }

    public static List<AdminArticleVO> fromList(List<Post> posts) {
        return posts.stream().map(AdminArticleVO::from).toList();
    }
}
