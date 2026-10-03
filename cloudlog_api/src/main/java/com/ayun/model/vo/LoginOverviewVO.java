package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 后台「登录日志」页签的概览：锁定策略 + 三张计数卡 + 失败来源 Top 榜。
 * 一次请求出齐，卡片与榜单因此必然出自同一份数据，可以互相对账
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录日志概览")
public class LoginOverviewVO {

    @Schema(description = "当前生效的锁定策略与合法区间")
    private LoginPolicyVO policy;

    @Schema(description = "今日失败次数：四种结果里除 success 之外全部计入", example = "18")
    private Integer todayFailures;

    @Schema(description = "今日「锁定期内的尝试」次数", example = "6")
    private Integer todayLocked;

    @Schema(description = "近一小时的失败来源数，按「邮箱 + 原始地址」组合去重", example = "4")
    private Integer recentFailureSources;

    @Schema(description = "失败次数前 10 的「邮箱 + 来源地址」组合，按保留期内全量聚合")
    private List<TopAttack> topAttacks;

    /**
     * 攻击 Top 榜的一行。来源就是聚合键的那一对：同一邮箱从两个地址来，算两行
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "登录失败来源聚合行")
    public static class TopAttack {

        @Schema(description = "被尝试的邮箱", example = "admin@ayun.com")
        private String email;

        @Schema(description = "脱敏后的来源地址", example = "203.0.*.*")
        private String ipMasked;

        @Schema(description = "该组合的失败次数", example = "23")
        private Integer failures;
    }

    /**
     * Top 榜的原始聚合行，带未脱敏的地址，只供服务层脱敏之用，不出现在任何响应里
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttackCount {

        private String email;

        /** 原始来源地址，不下发 */
        private String ip;

        private Integer failures;
    }
}
