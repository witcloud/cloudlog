package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 访问日志概览响应：时间范围一旦定下，四张卡、趋势与各 Top 榜都出自同一次窗口、同一条筛选，
 * 因此各卡数值之间可以直接交叉核对（趋势各柱之和 = PV，各 Top 榜的占比之和 ≤ 100%）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "访问日志概览")
public class VisitLogSummaryVO {

    @Schema(description = "概览四卡")
    private Overview overview;

    @Schema(description = "趋势各柱。今日视图为 0–23 共 24 柱，其余视图为窗口内每天一柱，无访问的时段补 0 不缺柱")
    private List<TrendPoint> trend;

    @Schema(description = "Top 文章，按访问数倒序取前 10")
    private List<TopPost> topPosts;

    @Schema(description = "Top IP，按访问数倒序取前 10，只出脱敏地址")
    private List<TopIp> topIps;

    @Schema(description = "Top 分类，按访问数倒序取前 10；无分类、分类已删、文章已删都在未分类那一格里（categoryName 为 null）")
    private List<TopCategory> topCategories;

    /**
     * 概览四卡。前一等长周期的对比只跟 PV 一项，其余三卡的涨跌不在本次口径内
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "概览指标卡")
    public static class Overview {

        @Schema(description = "访问次数（窗口内日志行数）", example = "128")
        private Long pv;

        @Schema(description = "访问者数（窗口内 IP 去重数）", example = "37")
        private Long uv;

        @Schema(description = "篇均访问 = PV ÷ 窗口内被访问过的文章数，保留一位小数；无人访问时为 0.0", example = "3.5")
        private Double avgVisitPerPost;

        @Schema(description = "PV 较前一等长周期的涨跌百分比，正升负降；前一周期为 0 次时无法求百分比，为 null", example = "12.5")
        private Double pvChangeRate;
    }

    /**
     * 趋势的一根柱子
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "趋势柱")
    public static class TrendPoint {

        @Schema(description = "柱下短标签：日视图为 MM-DD，小时视图为两位小时数", example = "10-03")
        private String label;

        @Schema(description = "悬浮提示用的完整时刻：日视图为 yyyy-MM-dd，小时视图为 yyyy-MM-dd HH:00", example = "2026-10-03 14:00")
        private String tooltip;

        @Schema(description = "该时段的访问次数", example = "18")
        private Long visitCount;
    }

    /**
     * Top 文章的一行
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Top 文章行")
    public static class TopPost {

        @Schema(description = "文章 ID（雪花 ID，字符串下发）", example = "7123456789012345678")
        private Long postId;

        @Schema(description = "文章标题；文章已删时为 null，前端按「已删除文章」呈现且不给跳转")
        private String title;

        @Schema(description = "窗口内访问次数", example = "42")
        private Long visitCount;

        @Schema(description = "占窗口内 PV 的百分比，保留一位小数", example = "32.8")
        private Double percent;
    }

    /**
     * Top IP 的一行。类里只有脱敏后的地址，没有能装下原始地址的字段——完整 IP 出不了这个类型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Top IP 行")
    public static class TopIp {

        @Schema(description = "脱敏后的来源地址，如 112.10.*.*", example = "112.10.*.*")
        private String ipMasked;

        @Schema(description = "窗口内访问次数", example = "17")
        private Long visitCount;

        @Schema(description = "占窗口内 PV 的百分比，保留一位小数", example = "13.3")
        private Double percent;
    }

    /**
     * Top 分类的一行
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Top 分类行")
    public static class TopCategory {

        @Schema(description = "分类名；无分类、分类已删、文章已删三种情况都归到未分类那一格，此时为 null")
        private String categoryName;

        @Schema(description = "窗口内访问次数", example = "60")
        private Long visitCount;

        @Schema(description = "占窗口内 PV 的百分比，保留一位小数", example = "46.9")
        private Double percent;
    }

    /**
     * 一次窗口 + 前一等长窗口的计数查询行（聚合结果，装配后转为 {@link Overview}）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WindowCount {

        /** 本窗口访问次数 */
        private Long pv;

        /** 本窗口访问者数 */
        private Long uv;

        /** 本窗口被访问过的文章数 */
        private Long postCount;

        /** 前一等长窗口访问次数 */
        private Long prevPv;
    }

    /**
     * 按原始地址分组的查询行。它带原始地址，只存在于服务层装配的那一步，
     * 出参一律换成 {@link TopIp}——这是「原始 IP 不进任何响应」的类型层保证
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpCount {

        /** 原始来源地址，不下发 */
        private String ip;

        /** 窗口内访问次数 */
        private Long visitCount;
    }

    /**
     * 趋势分桶的查询行（装配后转为 {@link TrendPoint}：桶键换成展示用的两串标签）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendCount {

        /** 日视图为 yyyy-MM-dd，小时视图为两位小时数 00–23 */
        private String bucket;

        /** 该桶的访问次数 */
        private Long visitCount;
    }
}
