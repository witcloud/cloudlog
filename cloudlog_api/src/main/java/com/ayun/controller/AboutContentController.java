package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.vo.AboutContentVO;
import com.ayun.model.vo.SiteInfoVO;
import com.ayun.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点级内容的公开读取（关于页文案与站点信息，均无需登录；SecurityConfig 按 GET /site/** 放行）
 */
@Tag(name = "站点文案", description = "关于页文案与站点信息的公开读取")
@RestController
@RequestMapping("/site")
public class AboutContentController {

    @Autowired
    private SettingsService settingsService;

    @Operation(summary = "获取关于页文案", description = "未配置的项返回内置默认文案，永不为空")
    @GetMapping("/about")
    public Result<AboutContentVO> getAboutContent() {
        return Result.success(settingsService.getAboutContent());
    }

    @Operation(summary = "获取站点信息", description = "站点名称与 ICP 备案号，供前台布局、页脚与评论框使用；"
            + "是 /admin/settings 的公开投影，不含只在后台用得上的配置")
    @GetMapping("/settings")
    public Result<SiteInfoVO> getSiteInfo() {
        return Result.success(settingsService.getSiteInfo());
    }
}
