package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章点赞实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章点赞记录")
public class Like {

    @Schema(description = "点赞ID", example = "1")
    private Integer id;

    @Schema(description = "文章ID（雪花 ID）", example = "7123456789012345678")
    private Long postId;

    @Schema(description = "用户ID（雪花 ID）", example = "7123456789012345678")
    private Long userId;

    @Schema(description = "点赞时间")
    private LocalDateTime createdAt;
}
