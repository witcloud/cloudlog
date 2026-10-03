package com.ayun.mapper;

import com.ayun.entity.VisitLog;
import com.ayun.model.vo.VisitLogItemVO;
import com.ayun.model.vo.VisitLogSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 访问日志 Mapper：写入侧只在文章详情命中阅读量判定时落一行，读取侧只服务后台分析
 */
@Mapper
public interface VisitLogMapper {

    /**
     * 记一条访问。{@code created_at} 不列出，走库默认值 {@code CURRENT_TIMESTAMP}
     */
    int insert(VisitLog visitLog);

    /**
     * 删除该时刻之前的日志行，单批最多 batchSize 条，返回删除条数；由日志保留期任务循环调到 0 行为止。
     * 单语句全量删在大流量下是长事务：行锁堆积、binlog 单笔巨大，分批让每批都短
     */
    int deleteCreatedBefore(@Param("threshold") LocalDateTime threshold, @Param("batchSize") int batchSize);

    /**
     * 概览卡的四个数：本窗口的 PV / UV / 被访问过的文章数，加上前一等长窗口的 PV。
     * 两条窗口在同一条语句里查出来（一次范围扫描，按时刻落在窗口哪一侧做条件聚合），
     * 因此涨跌的分子分母与概览其他三卡必然出自同一份数据
     */
    VisitLogSummaryVO.WindowCount countWindow(@Param("prevStart") LocalDateTime prevStart,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * 趋势：{@code byHour} 为真时按当日 0–23 时分组，否则按日分组。
     * 无人访问的桶不会出现在结果里，补 0 由服务层负责
     */
    List<VisitLogSummaryVO.TrendCount> findTrend(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("byHour") boolean byHour);

    /**
     * Top 文章。左连 {@code posts}：文章已删时标题为 null，那一条仍计入榜单（访问发生过）
     */
    List<VisitLogSummaryVO.TopPost> findTopPosts(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("limit") int limit);

    /**
     * 按原始地址分组的访问计数（窗口内全部分组，不截断）。
     * 脱敏会把同一网段的多个地址并成一格，所以「取前 10」必须等合并之后再取，不能在这里 LIMIT；
     * 返回的行带原始地址，不得直接下发
     */
    List<VisitLogSummaryVO.IpCount> countByIp(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    /**
     * Top 分类。两次左连：文章已删、分类已删、文章无分类三种情况都落在分类名为 null 的那一格
     */
    List<VisitLogSummaryVO.TopCategory> findTopCategories(@Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end,
                                                          @Param("limit") int limit);

    /**
     * 明细分页查询（PageHelper 消费紧随其后的第一条查询）。时间、文章、地址前缀、UA 四个条件可任意组合
     */
    List<VisitLogItemVO.VisitRow> findAdminList(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("postId") Long postId,
                                                @Param("ipPattern") String ipPattern,
                                                @Param("userAgent") String userAgent);
}
