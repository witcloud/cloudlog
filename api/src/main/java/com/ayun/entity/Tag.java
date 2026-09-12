package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标签实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "标签信息")
public class Tag {
    
    @Schema(description = "标签ID", example = "1")
    private Integer id;
    
    @Schema(description = "标签名称", example = "Redis")
    private String name;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
