package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.mapper.VisitLogMapper;
import com.ayun.model.dto.AdminVisitLogQueryDTO;
import com.ayun.model.vo.VisitLogItemVO;
import com.ayun.model.vo.VisitLogSummaryVO;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 后台访问日志分析的装配口径：脱敏、补 0、占比与涨跌都在服务层算完才下发。
 * 明细那组断言按「原始地址出不出得去」写，不按渲染结果写
 */
@ExtendWith(MockitoExtension.class)
class AdminVisitLogServiceTest {

    private static final LocalDateTime OCT_03 = LocalDateTime.of(2026, 10, 3, 0, 0);

    private static final LocalDateTime OCT_01 = LocalDateTime.of(2026, 10, 1, 0, 0);

    @Mock
    private VisitLogMapper visitLogMapper;

    @InjectMocks
    private AdminVisitLogService adminVisitLogService;

    @AfterEach
    void clearPageHelperThreadLocal() {
        // mock 出来的 Mapper 不会真正执行查询，PageHelper 挂在 ThreadLocal 上的分页参数得由测试自己清
        PageHelper.clearPage();
    }

    /**
     * 把概览与四个板块的返回一次置好，用例只 re-stub 自己要验的那一个。
     * 基础桩一律 lenient：同方法被后一条 when 覆盖时，先前的桩必然「没被用到」，严格模式下会当成缺陷报出来
     */
    private void stubAnalysis(long pv, long uv, long postCount, long prevPv) {
        lenient().when(visitLogMapper.countWindow(any(), any(), any()))
                .thenReturn(new VisitLogSummaryVO.WindowCount(pv, uv, postCount, prevPv));
        lenient().when(visitLogMapper.findTrend(any(), any(), anyBoolean())).thenReturn(List.of());
        lenient().when(visitLogMapper.findTopPosts(any(), any(), anyInt())).thenReturn(List.of());
        lenient().when(visitLogMapper.countByIp(any(), any())).thenReturn(List.of());
        lenient().when(visitLogMapper.findTopCategories(any(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("概览：篇均访问按窗口内被访问过的文章数算，涨跌按前一等长周期的 PV 算")
    void buildsOverviewFromWindowCount() {
        stubAnalysis(100L, 40L, 25L, 80L);

        VisitLogSummaryVO.Overview overview =
                adminVisitLogService.getSummary("2026-10-01", "2026-10-03").getOverview();

        assertEquals(100L, overview.getPv().longValue());
        assertEquals(40L, overview.getUv().longValue());
        assertEquals(4.0D, overview.getAvgVisitPerPost(), 0.001);
        assertEquals(25.0D, overview.getPvChangeRate(), 0.001);
    }

    @Test
    @DisplayName("前一周期一次访问都没有：涨跌是「求不出百分比」，不写成 0% 也不造一个数")
    void changeRateIsNullWhenPreviousWindowIsEmpty() {
        stubAnalysis(6L, 6L, 3L, 0L);

        VisitLogSummaryVO.Overview overview =
                adminVisitLogService.getSummary("2026-10-03", "2026-10-03").getOverview();

        assertEquals(6L, overview.getPv().longValue());
        assertNull(overview.getPvChangeRate());
    }

    @Test
    @DisplayName("窗口内无人访问：篇均是真 0，不是空值")
    void avgIsZeroWhenNothingWasVisited() {
        stubAnalysis(0L, 0L, 0L, 0L);

        assertEquals(0.0D, adminVisitLogService.getSummary("2026-10-03", "2026-10-03")
                .getOverview().getAvgVisitPerPost(), 0.001);
    }

    @Test
    @DisplayName("单日窗口出 24 根小时柱，无人访问的时段补 0 而不是缺柱")
    void fillsAllHoursForSingleDayWindow() {
        stubAnalysis(3L, 3L, 1L, 0L);
        when(visitLogMapper.findTrend(eq(OCT_03), any(), eq(true))).thenReturn(List.of(
                new VisitLogSummaryVO.TrendCount("07", 2L),
                new VisitLogSummaryVO.TrendCount("23", 1L)));

        List<VisitLogSummaryVO.TrendPoint> trend =
                adminVisitLogService.getSummary("2026-10-03", "2026-10-03").getTrend();

        assertEquals(24, trend.size());
        assertEquals("00", trend.get(0).getLabel());
        assertEquals(0L, trend.get(0).getVisitCount().longValue());
        assertEquals(2L, trend.get(7).getVisitCount().longValue());
        assertEquals("2026-10-03 07:00", trend.get(7).getTooltip());
        assertEquals(1L, trend.get(23).getVisitCount().longValue());
    }

    @Test
    @DisplayName("多日窗口按天出柱，跨度几天几根，缺的那天补 0")
    void fillsEveryDayForMultiDayWindow() {
        stubAnalysis(5L, 5L, 2L, 0L);
        when(visitLogMapper.findTrend(eq(OCT_01), any(), eq(false))).thenReturn(List.of(
                new VisitLogSummaryVO.TrendCount("2026-10-01", 5L)));

        List<VisitLogSummaryVO.TrendPoint> trend =
                adminVisitLogService.getSummary("2026-10-01", "2026-10-03").getTrend();

        assertEquals(3, trend.size());
        assertEquals("10-01", trend.get(0).getLabel());
        assertEquals(5L, trend.get(0).getVisitCount().longValue());
        assertEquals(0L, trend.get(1).getVisitCount().longValue());
        assertEquals("10-03", trend.get(2).getLabel());
    }

    @Test
    @DisplayName("Top IP：同一 /16 下的几个地址合并成一格，合并之后才截前 10")
    void mergesIpRowsSharingAMaskedPrefix() {
        stubAnalysis(10L, 10L, 1L, 0L);
        when(visitLogMapper.countByIp(any(), any())).thenReturn(List.of(
                new VisitLogSummaryVO.IpCount("112.10.1.5", 4L),
                new VisitLogSummaryVO.IpCount("112.10.9.9", 3L),
                new VisitLogSummaryVO.IpCount("203.0.113.7", 3L)));

        List<VisitLogSummaryVO.TopIp> topIps =
                adminVisitLogService.getSummary("2026-10-01", "2026-10-03").getTopIps();

        assertEquals(2, topIps.size());
        assertEquals("112.10.*.*", topIps.get(0).getIpMasked());
        assertEquals(7L, topIps.get(0).getVisitCount().longValue());
        assertEquals(70.0D, topIps.get(0).getPercent(), 0.001);
    }

    @Test
    @DisplayName("同一 /16 下的几十个地址先合并再截断：合并前截十行会把最大的一格判漏")
    void mergesIpGroupsBeforeTruncatingToTen() {
        stubAnalysis(70L, 30L, 1L, 0L);
        // 查询行按原始地址的计数倒序返回：十个各 5 次的独占网段在前，二十个各 1 次的同网段地址在后
        List<VisitLogSummaryVO.IpCount> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(new VisitLogSummaryVO.IpCount(i + ".0.0.1", 5L));
        }
        for (int i = 1; i <= 20; i++) {
            rows.add(new VisitLogSummaryVO.IpCount("9.9." + i + ".1", 1L));
        }
        when(visitLogMapper.countByIp(any(), any())).thenReturn(rows);

        List<VisitLogSummaryVO.TopIp> topIps =
                adminVisitLogService.getSummary("2026-10-01", "2026-10-03").getTopIps();

        assertEquals(10, topIps.size());
        assertEquals("9.9.*.*", topIps.get(0).getIpMasked());
        assertEquals(20L, topIps.get(0).getVisitCount().longValue());
        assertEquals(28.6D, topIps.get(0).getPercent(), 0.001);
    }

    @Test
    @DisplayName("明细行：地址已脱敏、UA 已归类，原始地址不在出参的任何字段里")
    void masksAddressOnEveryDetailRow() {
        String rawUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        when(visitLogMapper.findAdminList(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row(1, "112.10.20.30", rawUserAgent)));

        VisitLogItemVO item = adminVisitLogService.getList(query("2026-10-03", "2026-10-03"))
                .getList().get(0);

        assertEquals("112.10.*.*", item.getIpMasked());
        assertEquals("Chrome", item.getUserAgentType());
        assertEquals(rawUserAgent, item.getUserAgent());
        assertFalse(item.toString().contains("112.10.20.30"));
    }

    @Test
    @DisplayName("地址前缀筛选：按脱敏口径输入的前缀，换成原始值上的 LIKE 模式再查")
    void passesLikePatternForIpPrefix() {
        when(visitLogMapper.findAdminList(any(), any(), any(), eq("112.10.%"), any()))
                .thenReturn(List.of(row(2, "112.10.20.30", "curl/8.4.0")));

        AdminVisitLogQueryDTO query = query("2026-10-01", "2026-10-03");
        query.setIp("112.10");
        List<VisitLogItemVO> list = adminVisitLogService.getList(query).getList();

        assertEquals(1, list.size());
        assertEquals("Bot", list.get(0).getUserAgentType());
    }

    @Test
    @DisplayName("跨度超过 90 天直接拒，不给整表扫描留口子")
    void rejectsRangeOverNinetyDays() {
        BizException e = assertThrows(BizException.class,
                () -> adminVisitLogService.getSummary("2026-01-01", "2026-10-03"));
        assertEquals("时间跨度最大 90 天", e.getMessage());
    }

    @Test
    @DisplayName("结束日期早于开始日期：拒绝，不静默交换")
    void rejectsReversedRange() {
        BizException e = assertThrows(BizException.class,
                () -> adminVisitLogService.getSummary("2026-10-03", "2026-10-01"));
        assertEquals("结束日期早于开始日期", e.getMessage());
    }

    @Test
    @DisplayName("日期写错格式：按入参问题回中文原因，不是 500")
    void rejectsUnparsableDate() {
        BizException e = assertThrows(BizException.class,
                () -> adminVisitLogService.getSummary("10/03/2026", null));
        assertEquals("时间范围格式应为 yyyy-MM-dd", e.getMessage());
    }

    private static VisitLogItemVO.VisitRow row(int id, String ip, String userAgent) {
        VisitLogItemVO.VisitRow row = new VisitLogItemVO.VisitRow();
        row.setId(id);
        row.setIp(ip);
        row.setUserAgent(userAgent);
        return row;
    }

    private static AdminVisitLogQueryDTO query(String startDate, String endDate) {
        AdminVisitLogQueryDTO dto = new AdminVisitLogQueryDTO();
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        return dto;
    }
}
