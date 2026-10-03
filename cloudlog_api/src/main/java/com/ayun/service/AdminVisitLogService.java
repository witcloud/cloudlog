package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.IpMaskUtils;
import com.ayun.common.UserAgentClassifier;
import com.ayun.mapper.VisitLogMapper;
import com.ayun.model.dto.AdminVisitLogQueryDTO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.VisitLogItemVO;
import com.ayun.model.vo.VisitLogSummaryVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台访问日志分析。概览、趋势、Top 榜、明细四个板块共用同一个窗口解析口径，
 * 数值之间才可以交叉核对（趋势各柱之和 = PV，Top 榜占比之和 ≤ 100%）
 */
@Service
public class AdminVisitLogService {

    /**
     * 自定义区间的最大跨度（天）。上限是为了让每次分析都落在 {@code idx_created_at} 的一段范围扫描里，
     * 而不是把整张日志表扫一遍
     */
    private static final int MAX_RANGE_DAYS = 90;

    /**
     * 各 Top 榜的条数
     */
    private static final int TOP_LIMIT = 10;

    @Autowired
    private VisitLogMapper visitLogMapper;

    /**
     * 概览 + 趋势 + 三个 Top 榜，一次窗口一次查完。
     * 合成一个接口而不拆五个，是为了让四张卡、柱图与榜单必然出自同一次范围扫描——
     * 分开请求时前端会拿到两次略有不同的时刻，卡片与趋势图就对不上账了
     */
    public VisitLogSummaryVO getSummary(String startDate, String endDate) {
        Window window = Window.of(startDate, endDate);
        VisitLogSummaryVO.WindowCount count = visitLogMapper.countWindow(window.prevStart, window.start, window.end);
        long pv = count.getPv() == null ? 0L : count.getPv();
        long postCount = count.getPostCount() == null ? 0L : count.getPostCount();
        long prevPv = count.getPrevPv() == null ? 0L : count.getPrevPv();

        VisitLogSummaryVO.Overview overview = VisitLogSummaryVO.Overview.builder()
                .pv(pv)
                .uv(count.getUv() == null ? 0L : count.getUv())
                .avgVisitPerPost(postCount == 0L ? 0D : oneDecimal((double) pv / postCount))
                .pvChangeRate(prevPv == 0L ? null : oneDecimal((pv - prevPv) * 100D / prevPv))
                .build();

        return VisitLogSummaryVO.builder()
                .overview(overview)
                .trend(buildTrend(window))
                .topPosts(buildTopPosts(window, pv))
                .topIps(buildTopIps(window, pv))
                .topCategories(buildTopCategories(window, pv))
                .build();
    }

    /**
     * 明细分页：时间范围跟随全局，文章、地址前缀、UA 三个条件可任意组合，默认时间倒序
     */
    public PageResult<VisitLogItemVO> getList(AdminVisitLogQueryDTO queryDTO) {
        Window window = Window.of(queryDTO.getStartDate(), queryDTO.getEndDate());
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<VisitLogItemVO.VisitRow> rows = visitLogMapper.findAdminList(
                window.start, window.end, queryDTO.getPostId(),
                IpMaskUtils.likePattern(queryDTO.getIp()), queryDTO.getUserAgent());
        PageInfo<VisitLogItemVO.VisitRow> pageInfo = new PageInfo<>(rows);
        List<VisitLogItemVO> list = rows.stream().map(AdminVisitLogService::toItem).collect(Collectors.toList());
        return PageResult.of(pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize(), list);
    }

    /**
     * 一行原始记录换成一行出参：地址脱敏、UA 归类。原始地址在这一行之后就没有下一个去处了
     */
    private static VisitLogItemVO toItem(VisitLogItemVO.VisitRow row) {
        return VisitLogItemVO.builder()
                .id(row.getId())
                .createdAt(row.getCreatedAt())
                .postId(row.getPostId())
                .postTitle(row.getPostTitle())
                .ipMasked(IpMaskUtils.mask(row.getIp()))
                .userAgentType(UserAgentClassifier.classify(row.getUserAgent()))
                .userAgent(row.getUserAgent())
                .build();
    }

    /**
     * 单日窗口按小时出 24 柱，多日窗口按天出柱；两种都补齐没有访问的桶，缺柱会让高峰看着比实际更高
     */
    private List<VisitLogSummaryVO.TrendPoint> buildTrend(Window window) {
        Map<String, Long> counted = visitLogMapper.findTrend(window.start, window.end, window.isSingleDay())
                .stream()
                .collect(Collectors.toMap(VisitLogSummaryVO.TrendCount::getBucket,
                        row -> row.getVisitCount() == null ? 0L : row.getVisitCount(),
                        (a, b) -> a));
        List<VisitLogSummaryVO.TrendPoint> points = new ArrayList<>();
        if (window.isSingleDay()) {
            String day = window.start.toLocalDate().toString();
            for (int hour = 0; hour < 24; hour++) {
                String bucket = String.format("%02d", hour);
                points.add(new VisitLogSummaryVO.TrendPoint(
                        bucket, day + " " + bucket + ":00", counted.getOrDefault(bucket, 0L)));
            }
            return points;
        }
        for (LocalDate date = window.start.toLocalDate(); date.isBefore(window.end.toLocalDate()); date = date.plusDays(1)) {
            String bucket = date.toString();
            points.add(new VisitLogSummaryVO.TrendPoint(
                    bucket.substring(5), bucket, counted.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<VisitLogSummaryVO.TopPost> buildTopPosts(Window window, long pv) {
        List<VisitLogSummaryVO.TopPost> posts = visitLogMapper.findTopPosts(window.start, window.end, TOP_LIMIT);
        posts.forEach(post -> post.setPercent(shareOf(post.getVisitCount(), pv)));
        return posts;
    }

    /**
     * 先按原始地址分组，再脱敏合并——同一 /16 下的几个地址在博主眼里就是同一格，
     * 合并前截断十行会让这一格的次数比实际少
     */
    private List<VisitLogSummaryVO.TopIp> buildTopIps(Window window, long pv) {
        Map<String, Long> merged = new LinkedHashMap<>();
        for (VisitLogSummaryVO.IpCount row : visitLogMapper.countByIp(window.start, window.end)) {
            String masked = IpMaskUtils.mask(row.getIp());
            merged.merge(masked, row.getVisitCount() == null ? 0L : row.getVisitCount(), Long::sum);
        }
        return merged.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_LIMIT)
                .map(entry -> VisitLogSummaryVO.TopIp.builder()
                        .ipMasked(entry.getKey())
                        .visitCount(entry.getValue())
                        .percent(shareOf(entry.getValue(), pv))
                        .build())
                .collect(Collectors.toList());
    }

    private List<VisitLogSummaryVO.TopCategory> buildTopCategories(Window window, long pv) {
        List<VisitLogSummaryVO.TopCategory> categories = visitLogMapper.findTopCategories(window.start, window.end, TOP_LIMIT);
        categories.forEach(category -> category.setPercent(shareOf(category.getVisitCount(), pv)));
        return categories;
    }

    private static Double shareOf(Long count, long pv) {
        if (pv == 0L) {
            return 0D;
        }
        return oneDecimal((count == null ? 0L : count) * 100D / pv);
    }

    /**
     * 保留一位小数：卡片与进度条上的百分比一律由后端算好，前端不再估
     */
    private static double oneDecimal(double value) {
        return Math.round(value * 10D) / 10D;
    }

    /**
     * 分析窗口：起含、止不含（止日期整天之后一秒），与后台文章列表的 startDate/endDate 口径一致。
     * 单日窗口按小时分桶，其余按天
     */
    private static final class Window {

        private final LocalDateTime start;

        private final LocalDateTime end;

        private final LocalDateTime prevStart;

        private final long days;

        private Window(LocalDateTime start, LocalDateTime end, LocalDateTime prevStart, long days) {
            this.start = start;
            this.end = end;
            this.prevStart = prevStart;
            this.days = days;
        }

        static Window of(String startDate, String endDate) {
            // 「今天」锚业务时区：日志行的 created_at 按库会话的 Asia/Shanghai 落，JVM 以别的时区跑时
            // 用默认时区取今天会把窗口整体错开数小时
            LocalDate today = LocalDate.now(ArticleService.BUSINESS_ZONE);
            LocalDate from = parseOr(startDate, today);
            LocalDate to = parseOr(endDate, from);
            if (to.isBefore(from)) {
                throw new BizException("结束日期早于开始日期");
            }
            long days = Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() + 1;
            if (days > MAX_RANGE_DAYS) {
                throw new BizException("时间跨度最大 " + MAX_RANGE_DAYS + " 天");
            }
            LocalDateTime start = from.atStartOfDay();
            LocalDateTime end = to.plusDays(1).atStartOfDay();
            return new Window(start, end, start.minusDays(days), days);
        }

        private static LocalDate parseOr(String value, LocalDate fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException e) {
                throw new BizException("时间范围格式应为 yyyy-MM-dd");
            }
        }

        boolean isSingleDay() {
            return days == 1L;
        }
    }
}
