package com.ayun.mapper;

import com.ayun.entity.LoginLog;
import com.ayun.model.vo.LoginLogVO;
import com.ayun.model.vo.LoginOverviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志 Mapper：写入侧只在 {@code AuthService.login()} 的四个出口各落一行，读取侧只服务后台
 */
@Mapper
public interface LoginLogMapper {

    /**
     * 记一条登录尝试。{@code created_at} 不列出，走库默认值 {@code CURRENT_TIMESTAMP}
     */
    int insert(LoginLog loginLog);

    /**
     * 今日失败次数：四种结果里除 success 之外全部计入
     */
    int countTodayFailures();

    /**
     * 今日「锁定期内的尝试」次数
     */
    int countTodayLocked();

    /**
     * 近一小时的失败来源数，按「邮箱 + 原始地址」组合去重
     */
    int countRecentFailureSources();

    /**
     * 按「邮箱 + 原始地址」分组的失败计数，取前 N。返回的行带原始地址，不得直接下发
     */
    List<LoginOverviewVO.AttackCount> countBySource(@Param("limit") int limit);

    /**
     * 明细分页查询（PageHelper 消费紧随其后的第一条查询）。时间、邮箱、地址前缀、结果四个条件可任意组合；
     * 空白条件由服务层传 null，按「不筛」处理
     */
    List<LoginLogVO.LoginRow> findAdminList(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("email") String email,
                                            @Param("ipPattern") String ipPattern,
                                            @Param("result") String result);

    /**
     * 删除该时刻之前的日志行，单批最多 batchSize 条，返回删除条数；由日志保留期任务循环调到 0 行为止。
     * 单语句全量删在大流量下是长事务：行锁堆积、binlog 单笔巨大，分批让每批都短
     */
    int deleteCreatedBefore(@Param("threshold") LocalDateTime threshold, @Param("batchSize") int batchSize);
}
