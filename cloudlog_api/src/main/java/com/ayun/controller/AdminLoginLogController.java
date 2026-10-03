package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.dto.AdminLoginLogQueryDTO;
import com.ayun.model.vo.LoginLogVO;
import com.ayun.model.vo.LoginOverviewVO;
import com.ayun.model.vo.LoginPolicyVO;
import com.ayun.model.vo.PageResult;
import com.ayun.service.LoginLogService;
import com.ayun.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台「日志管理 / 登录日志」：登录尝试的读取，与登录锁定策略的读写。
 * 三条路径的公共前缀就是 {@code /admin}，锁定策略的键在 {@code site_settings} 而不在日志表，硬套同一个子前缀反而别扭
 */
@Tag(name = "后台-登录日志", description = "登录成败与爆破尝试的查询，以及登录锁定策略的读写")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLoginLogController {

    @Autowired
    private LoginLogService loginLogService;

    @Autowired
    private SettingsService settingsService;

    @Operation(summary = "登录日志概览",
            description = "当前锁定策略与合法区间 + 今日失败/今日锁定/近一小时失败来源数 + 失败来源 Top 10")
    @GetMapping("/login-logs/overview")
    public Result<LoginOverviewVO> getOverview() {
        return Result.success(loginLogService.getOverview());
    }

    @Operation(summary = "分页查询登录日志",
            description = "时间范围、邮箱、来源地址前缀、结果四个筛选可任意组合；来源地址只出脱敏值，原始 IP 不进响应")
    @GetMapping("/login-logs")
    public Result<PageResult<LoginLogVO>> getLogs(@Valid AdminLoginLogQueryDTO queryDTO) {
        return Result.success(loginLogService.getLogs(queryDTO));
    }

    @Operation(summary = "保存登录锁定策略",
            description = "失败次数限 3-10、锁定时长限 5-60 分钟，越界回 400；改动只影响之后新开的计数窗口，已锁定键的剩余时长不变")
    @PutMapping("/login-policy")
    public Result<Void> updateLoginPolicy(@RequestBody LoginPolicyVO policy) {
        settingsService.updateLoginPolicy(policy);
        return Result.success();
    }
}
