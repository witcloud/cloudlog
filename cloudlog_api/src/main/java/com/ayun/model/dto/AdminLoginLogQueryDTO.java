package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理后台登录日志查询参数 DTO（四个筛选可任意组合）
 */
@Data
@Schema(description = "管理后台登录日志查询参数")
public class AdminLoginLogQueryDTO {

    // 日志表只增不减，是几张表里行数最多的，页容量上下限比列表页更要给
    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    @Schema(description = "每页条数，最大 50", defaultValue = "15")
    private Integer pageSize = 15;

    @Schema(description = "尝试时刻起（yyyy-MM-dd）", example = "2026-09-13")
    private String startDate;

    @Schema(description = "尝试时刻止（yyyy-MM-dd，含当天）", example = "2026-10-03")
    private String endDate;

    @Schema(description = "邮箱关键词，按尝试时输入的邮箱模糊匹配")
    private String email;

    @Schema(description = "来源地址前缀，可粘列表里的脱敏串（如 203.0.*.*），按前缀匹配原始地址", example = "203.0")
    private String ipPrefix;

    @Schema(description = "结果筛选: success, wrong_password, locked, disabled")
    private String result;
}
