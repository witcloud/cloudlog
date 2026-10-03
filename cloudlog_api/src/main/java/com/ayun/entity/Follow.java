package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户关注关系实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户关注关系记录")
public class Follow {

    @Schema(description = "关注ID", example = "1")
    private Integer id;

    @Schema(description = "被关注用户ID（雪花 ID）", example = "7123456789012345678")
    private Long authorId;

    @Schema(description = "关注者用户ID（雪花 ID）", example = "7123456789012345678")
    private Long followerId;

    @Schema(description = "关注时间")
    private LocalDateTime createdAt;
}
