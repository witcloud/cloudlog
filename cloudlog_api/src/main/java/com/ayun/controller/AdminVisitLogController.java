package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.dto.AdminVisitLogQueryDTO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.VisitLogItemVO;
import com.ayun.model.vo.VisitLogSummaryVO;
import com.ayun.service.AdminVisitLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台访问日志分析。响应里的来源地址一律已脱敏
 */
@Tag(name = "后台-访问日志", description = "管理员查看文章访问的概览、趋势、Top 榜与明细")
@RestController
@RequestMapping("/admin/logs/visits")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVisitLogController {

    @Autowired
    private AdminVisitLogService adminVisitLogService;

    @Operation(summary = "概览、趋势与 Top 榜", description = "四张卡、柱图与三个 Top 榜共用同一时间窗口，一次返回；跨度上限 90 天")
    @Parameter(name = "startDate", description = "时间起（yyyy-MM-dd，含当日），缺省为当日", example = "2026-09-26")
    @Parameter(name = "endDate", description = "时间止（yyyy-MM-dd，含当日），缺省为起始日", example = "2026-10-03")
    @GetMapping("/summary")
    public Result<VisitLogSummaryVO> getSummary(@RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        return Result.success(adminVisitLogService.getSummary(startDate, endDate));
    }

    @Operation(summary = "分页查询访问明细", description = "时间、文章、来源地址前缀、UA 关键词四个条件可组合；地址脱敏、UA 归类都由后端完成")
    @GetMapping
    public Result<PageResult<VisitLogItemVO>> getList(@Valid AdminVisitLogQueryDTO queryDTO) {
        return Result.success(adminVisitLogService.getList(queryDTO));
    }
}
