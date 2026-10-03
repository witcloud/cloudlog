package com.ayun.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import lombok.Data;

import java.time.LocalDateTime;
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

    /** 摘要不在入参里：服务端派生，规则同创建接口（business-model.md §2.3） */
    @Schema(description = "文章封面图")
    private String cover;

    @Schema(description = "分类ID")
    private Integer categoryId;

    @Schema(description = "文章状态: draft(草稿), published(已发布), scheduled(定时发布), offline(已下线)")
    private String status;

    /** 与创建接口同口径；PUT 未挂 @Valid，该规则由服务层裁决，见 business-model.md §4.2 */
    @Future(message = "定时发布时间需晚于当前时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "定时发布时间，格式 yyyy-MM-dd HH:mm:ss，仅 status=scheduled 时使用，须晚于当前时间",
            example = "2026-10-05 09:30:00")
    private LocalDateTime publishTime;

    @Schema(description = "是否置顶: 0(否), 1(是)")
    private Integer isTop;

    @Schema(description = "置顶排序权重，越大越靠前；仅 isTop=1 时参与排序。不带该字段即不改既有权重")
    private Integer topOrder;

    @Schema(description = "标签ID列表")
    private List<Integer> tagIds;
}
