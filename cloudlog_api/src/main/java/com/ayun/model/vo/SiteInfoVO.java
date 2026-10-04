package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开站点信息（前台布局、页脚与评论框读取，不含任何只在后台用得上的配置）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公开站点信息")
public class SiteInfoVO {

    @Schema(description = "站点名称，未配置时为内置默认名", example = "CloudLog")
    private String siteName;

    @Schema(description = "ICP 备案号，空串表示页脚不展示备案信息", example = "京ICP备2024000000号")
    private String icpNumber;

    @Schema(description = "评论字数上限，0 表示不限制；前台评论框据此限制输入，服务端仍独立校验", example = "500")
    private Integer commentMaxLength;

    @Schema(description = "同一用户两次发评论的最小间隔秒数，0 表示不限制；前台据此置灰提交按钮", example = "60")
    private Integer commentIntervalSeconds;

    @Schema(description = "开放注册开关：关闭时前台隐藏注册入口与发码按钮，而不是让人发完验证码才被拒", example = "true")
    private Boolean registrationEnabled;
}
