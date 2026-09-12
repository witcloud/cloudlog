package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评论信息")
public class Comment {
    
    @Schema(description = "评论ID", example = "1")
    private Integer id;
    
    @Schema(description = "文章ID", example = "1")
    private Integer postId;
    
    @Schema(description = "用户ID", example = "1")
    private Integer userId;
    
    @Schema(description = "评论内容", example = "写得真不错！")
    private String content;
    
    @Schema(description = "父评论ID", example = "0")
    private Integer parentId;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "评论者信息")
    private User user;
    
    @Schema(description = "回复列表")
    private List<Comment> replies;
}
