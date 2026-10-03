package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台用户查询参数 DTO
 */
@Data
@Schema(description = "管理后台用户查询参数")
public class AdminUserQueryDTO {

    // 页容量上下限与公开列表同口径：不给上限时一次 pageSize=100000 就把全站用户连邮箱一起装进响应
    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    @Schema(description = "每页条数，最大 50", defaultValue = "15")
    private Integer pageSize = 15;

    @Schema(description = "关键词（匹配用户名/邮箱）")
    private String keyword;

    @Schema(description = "状态筛选：1-正常, 0-禁用")
    private Integer status;
}
