package com.ayun.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建文章 DTO
 */
@Data
@Schema(description = "创建文章参数")
public class PostCreateDTO {

    @NotBlank(message = "文章标题不能为空")
    @Schema(description = "文章标题")
    private String title;

    @NotBlank(message = "文章内容不能为空")
    @Schema(description = "文章内容")
    private String content;

    /** 摘要不在入参里：它由服务端在对外发布时从正文生成（business-model.md §2.3），客户端传了也不认 */
    @Schema(description = "文章封面图")
    private String cover;

    /** 草稿免分类；服务层只在 status 为 published / scheduled 时强制非空（business-model.md §4.2） */
    @Schema(description = "分类ID，发布或定时发布时必填，草稿可空")
    private Integer categoryId;

    @Schema(description = "文章状态: draft(草稿), published(已发布), scheduled(定时发布)")
    private String status = "draft";

    /** 计划时刻只在流转到 scheduled 时才要求未来值；状态相关的校验在服务层，见 business-model.md §4.2 */
    @Future(message = "定时发布时间需晚于当前时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "定时发布时间，格式 yyyy-MM-dd HH:mm:ss，仅 status=scheduled 时使用，须晚于当前时间",
            example = "2026-10-05 09:30:00")
    private LocalDateTime publishTime;

    @Schema(description = "是否置顶: 0(否), 1(是)")
    private Integer isTop = 0;

    @Schema(description = "标签ID列表")
    private List<Integer> tagIds;
}
