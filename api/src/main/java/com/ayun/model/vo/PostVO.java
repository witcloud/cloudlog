package com.ayun.model.vo;

import com.ayun.entity.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章视图对象")
public class PostVO {

    @Schema(description = "文章ID")
    private Integer id;

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章摘要")
    private String excerpt;

    @Schema(description = "文章正文内容")
    private String content;

    @Schema(description = "文章封面图")
    private String cover;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "作者信息")
    private AuthorVO author;

    @Schema(description = "分类信息")
    private CategoryVO category;

    @Schema(description = "标签列表")
    private List<Tag> tags;

    @Schema(description = "阅读量")
    private Integer views;

    @Schema(description = "点赞数")
    private Integer likes;

    @Schema(description = "评论数")
    private Integer comments;

    @Schema(description = "是否置顶")
    private Integer isTop;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorVO {
        private Integer id;
        private String username;
        private String avatar;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryVO {
        private Integer id;
        private String name;
    }
}
