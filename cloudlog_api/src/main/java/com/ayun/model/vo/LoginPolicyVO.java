package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录锁定策略。既作概览接口回给前段的当前值与合法区间，也作 PUT /admin/login-policy 的请求体：
 * 区间随响应下发，是为了让说明文字里的「3-10 / 5-60」只有一份来源，不在前端再抄一遍数字
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录锁定策略")
public class LoginPolicyVO {

    @Schema(description = "触发锁定的连续失败次数", example = "5")
    private Integer maxFailures;

    @Schema(description = "锁定时长（分钟），同时也是未达阈值时的计数窗口", example = "15")
    private Integer lockMinutes;

    @Schema(description = "失败次数下限", example = "3")
    private Integer maxFailuresMin;

    @Schema(description = "失败次数上限", example = "10")
    private Integer maxFailuresMax;

    @Schema(description = "锁定时长下限（分钟）", example = "5")
    private Integer lockMinutesMin;

    @Schema(description = "锁定时长上限（分钟）", example = "60")
    private Integer lockMinutesMax;
}
