package com.ayun.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    
    @Schema(description = "文章ID（雪花 ID）", example = "7123456789012345678")
    private Long postId;
    
    @Schema(description = "用户ID（雪花 ID）", example = "7123456789012345678")
    private Long userId;
    
    @Schema(description = "评论内容", example = "写得真不错！")
    private String content;
    
    @Schema(description = "父评论ID", example = "0")
    private Integer parentId;

    @Schema(description = "审核状态: pending(待审核), approved(已通过), rejected(已拒绝)", example = "approved")
    private String status;

    // 只在写路径用（机审落 pending 时写入），公开的评论查询不装载它，故 NON_NULL 让前台响应体与改造前一致；
    // 命中词是审核台的字段，外泄会让发评人反查出词库里的词
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "机审命中的首个违禁词；仅落 pending 时由服务端写入，不接受客户端指定", example = "广告")
    private String hitWord;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "评论者信息")
    private User user;
    
    @Schema(description = "回复列表")
    private List<Comment> replies;
}
