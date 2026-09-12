package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 更新文章 DTO
 */
@Data
@Schema(description = "更新文章参数")
public class PostUpdateDTO {

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章内容")
    private String content;

    @Schema(description = "文章摘要")
    private String excerpt;

    @Schema(description = "文章封面图")
    private String cover;

    @Schema(description = "分类ID")
    private Integer categoryId;

    @Schema(description = "文章状态: draft(草稿), published(发布)")
    private String status;

    @Schema(description = "是否置顶: 0(否), 1(是)")
    private Integer isTop;

    @Schema(description = "标签ID列表")
    private List<Integer> tagIds;
}
