package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章信息")
public class Post {
    
    @Schema(description = "文章ID", example = "1")
    private Integer id;
    
    @Schema(description = "文章标题", example = "Spring Boot 3 入门指南")
    private String title;
    
    @Schema(description = "文章内容", example = "Markdown 格式的正文...")
    private String content;
    
    @Schema(description = "文章摘要", example = "这是一篇关于 Spring Boot 3 的入门教程")
    private String excerpt;

    @Schema(description = "文章封面图")
    private String cover;
    
    @Schema(description = "作者ID", example = "1")
    private Integer authorId;
    
    @Schema(description = "分类ID", example = "1")
    private Integer categoryId;
    
    @Schema(description = "文章状态", example = "published", allowableValues = {"draft", "published", "scheduled"})
    private String status; 
    
    @Schema(description = "是否置顶", example = "0", allowableValues = {"0", "1"})
    private Integer isTop; 
    
    @Schema(description = "阅读量", example = "100")
    private Integer viewCount;
    
    @Schema(description = "点赞数", example = "10")
    private Integer likeCount;
    
    @Schema(description = "评论数", example = "5")
    private Integer commentCount;
    
    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "作者信息")
    private User author;
    
    @Schema(description = "分类信息")
    private Category category;
    
    @Schema(description = "标签列表")
    private List<Tag> tags;

    @Schema(description = "SEO 信息")
    private SeoMeta seo;
}
