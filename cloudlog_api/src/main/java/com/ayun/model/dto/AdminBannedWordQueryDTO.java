package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台违禁词库查询参数 DTO
 */
@Data
@Schema(description = "管理后台违禁词库查询参数")
public class AdminBannedWordQueryDTO {

    @Min(value = 1, message = "页码最小为 1")
    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    @Schema(description = "每页条数", defaultValue = "15")
    private Integer pageSize = 15;

    @Schema(description = "关键词（按词面模糊匹配）")
    private String keyword;
}
