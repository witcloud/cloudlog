package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.vo.DashboardVO;
import com.ayun.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台工作台概览
 */
@Tag(name = "后台-工作台", description = "管理员工作台的统计概览")
@RestController
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Operation(summary = "获取工作台概览数据")
    @GetMapping
    public Result<DashboardVO> getDashboard() {
        return Result.success(adminDashboardService.getDashboard());
    }
}
