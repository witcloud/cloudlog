package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SEO 优化实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SEO 信息")
public class SeoMeta {
    
    @Schema(description = "SEO ID", example = "1")
    private Integer id;
    
    @Schema(description = "文章ID", example = "1")
    private Integer postId;
    
    @Schema(description = "SEO 标题", example = "Spring Boot 教程")
    private String title;
    
    @Schema(description = "SEO 描述", example = "全面深入的 Spring Boot 教程")
    private String description;
    
    @Schema(description = "SEO 关键词", example = "Spring Boot, Java, 教程")
    private String keywords;
}
