package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 访问日志实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "访问日志")
public class VisitLog {
    
    @Schema(description = "记录ID", example = "1")
    private Integer id;
    
    @Schema(description = "文章ID（雪花 ID）", example = "7123456789012345678")
    private Long postId;
    
    @Schema(description = "访问者 IP", example = "127.0.0.1")
    private String ip;
    
    @Schema(description = "浏览器 User Agent")
    private String userAgent;
    
    @Schema(description = "访问时间")
    private LocalDateTime createdAt;
}
