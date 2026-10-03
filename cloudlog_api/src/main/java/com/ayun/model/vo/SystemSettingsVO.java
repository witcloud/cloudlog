package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统设置视图对象（后台一张表单读写全站运行参数：开关、站点信息、评论防护；
 * 违禁词库属内容数据，见 BannedWord）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统设置")
public class SystemSettingsVO {

    @Schema(description = "评论审核：开启后新评论先经违禁词机审，命中 pending 词转人工待审、命中 reject 词直接拒绝", example = "true")
    private Boolean commentModeration;

    @Schema(description = "开放注册：关闭后新用户无法注册", example = "true")
    private Boolean registrationEnabled;

    @Schema(description = "站点名称：前台标题后缀与页脚落款使用，留空回落 CloudLog", example = "CloudLog")
    private String siteName;

    @Schema(description = "ICP 备案号：留空则页脚不展示备案信息", example = "京ICP备2024000000号")
    private String icpNumber;

    @Schema(description = "站点对外域名，含协议、末尾不带斜杠；sitemap / RSS 的绝对链接与 robots 的 Sitemap 行由它拼。"
            + "留空即按当次请求的来源地址兜底", example = "https://cloudlog.site")
    private String siteUrl;

    @Schema(description = "评论字数上限，0 表示不限制", example = "500")
    private Integer commentMaxLength;

    @Schema(description = "同一用户评论发布间隔（秒），0 表示不限制", example = "60")
    private Integer commentIntervalSeconds;
}
