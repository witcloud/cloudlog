package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 访问日志明细的查询参数。时间窗口的口径与概览那侧完全一致（同一对 startDate/endDate），
 * 只是多三个可组合的筛选条件
 */
@Data
@Schema(description = "访问日志明细查询参数")
public class AdminVisitLogQueryDTO {

    // 日志表只增不减，页容量不给上限时一次明细拉取就能扫完整表
    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    @Schema(description = "每页条数，最大 50", defaultValue = "15")
    private Integer pageSize = 15;

    @Schema(description = "时间起（yyyy-MM-dd，含当日）；缺省为当日", example = "2026-09-26")
    private String startDate;

    @Schema(description = "时间止（yyyy-MM-dd，含当日）；缺省为当日", example = "2026-10-03")
    private String endDate;

    @Schema(description = "按文章筛选（雪花 ID，字符串下发）", example = "7123456789012345678")
    private Long postId;

    @Schema(description = "来源地址前缀，按脱敏口径输入，如 112.10 或 112.10.*.*")
    private String ip;

    @Schema(description = "UA 关键词，对原始 UA 串做包含匹配")
    private String userAgent;
}
