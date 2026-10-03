package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.vo.SystemSettingsVO;
import com.ayun.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台系统设置
 */
@Tag(name = "后台-系统设置", description = "影响系统运行的核心配置")
@RestController
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    @Autowired
    private SettingsService settingsService;

    @Operation(summary = "获取系统设置")
    @GetMapping
    public Result<SystemSettingsVO> getSettings() {
        return Result.success(settingsService.getSettings());
    }

    @Operation(summary = "更新系统设置", description = "仅更新传入的字段")
    @PutMapping
    public Result<Void> updateSettings(@RequestBody SystemSettingsVO settingsVO) {
        settingsService.updateSettings(settingsVO);
        return Result.success();
    }
}
