package com.ayun.service;

import com.ayun.mapper.LoginLogMapper;
import com.ayun.mapper.VisitLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 日志保留期任务：各类日志按同一模式每天清一次超期行。
 * 新增一类日志（如登录日志）就是加一个保留天数常量 + 一个 {@code purgeXxx()}，
 * 再把它列进 {@link #purgeExpiredLogs()} —— 不另起一张调度表、不引任务框架
 */
@Slf4j
@Service
public class LogRetentionService {

    /** 访问日志保留天数：后台看的是近因，两年前的逐条访问没有运营价值 */
    private static final int VISIT_LOG_RETENTION_DAYS = 180;

    /** 登录日志保留天数：与访问日志同一天数，两个页签答的是「近半年」这同一个问题 */
    private static final int LOGIN_LOG_RETENTION_DAYS = 180;

    @Autowired
    private VisitLogMapper visitLogMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    /**
     * 每天 03:30 跑一轮。每类各自兜住异常，一类清理失败不影响其余类
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void purgeExpiredLogs() {
        purgeVisitLogs();
        purgeLoginLogs();
    }

    private void purgeVisitLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(VISIT_LOG_RETENTION_DAYS);
        try {
            int removed = visitLogMapper.deleteCreatedBefore(threshold);
            if (removed > 0) {
                log.info("访问日志保留期清理：删除 {} 条早于 {} 的记录", removed, threshold);
            }
        } catch (Exception e) {
            log.error("访问日志保留期清理失败（阈值 {}）：{}", threshold, e.getMessage(), e);
        }
    }

    private void purgeLoginLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(LOGIN_LOG_RETENTION_DAYS);
        try {
            int removed = loginLogMapper.deleteCreatedBefore(threshold);
            if (removed > 0) {
                log.info("登录日志保留期清理：删除 {} 条早于 {} 的记录", removed, threshold);
            }
        } catch (Exception e) {
            log.error("登录日志保留期清理失败（阈值 {}）：{}", threshold, e.getMessage(), e);
        }
    }
}
