package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.vo.AboutContentVO;
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
 * 管理后台关于页文案
 */
@Tag(name = "后台-关于页文案", description = "前台关于页展示的站点级文本")
@RestController
@RequestMapping("/admin/about-content")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAboutContentController {

    @Autowired
    private SettingsService settingsService;

    @Operation(summary = "获取关于页文案")
    @GetMapping
    public Result<AboutContentVO> getAboutContent() {
        return Result.success(settingsService.getAboutContent());
    }

    @Operation(summary = "更新关于页文案", description = "仅更新传入的字段；传空串恢复该项默认文案")
    @PutMapping
    public Result<Void> updateAboutContent(@Valid @RequestBody AboutContentVO aboutContentVO) {
        settingsService.updateAboutContent(aboutContentVO);
        return Result.success();
    }
}
