package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台评论查询参数 DTO
 */
@Data
@Schema(description = "管理后台评论查询参数")
public class AdminCommentQueryDTO {

    // 页容量上下限与公开列表同口径：不给上限时一次 pageSize=100000 就把全站评论装进响应
    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    @Schema(description = "每页条数，最大 50", defaultValue = "15")
    private Integer pageSize = 15;

    @Schema(description = "审核状态筛选: pending, approved, rejected")
    private String status;

    @Schema(description = "关键词（匹配评论内容/评论者/文章标题）")
    private String keyword;
}
