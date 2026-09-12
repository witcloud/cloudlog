package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章分类实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章分类")
public class Category {
    
    @Schema(description = "分类ID", example = "1")
    private Integer id;
    
    @Schema(description = "分类名称", example = "Java")
    private String name;
    
    @Schema(description = "分类描述", example = "Java 相关技术文章")
    private String description;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "文章数量", example = "10")
    private Integer articleCount;
}
