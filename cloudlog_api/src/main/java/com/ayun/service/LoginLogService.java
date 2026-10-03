package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.IpMaskUtils;
import com.ayun.common.UserAgentClassifier;
import com.ayun.entity.LoginLog;
import com.ayun.mapper.LoginLogMapper;
import com.ayun.model.dto.AdminLoginLogQueryDTO;
import com.ayun.model.vo.LoginLogVO;
import com.ayun.model.vo.LoginOverviewVO;
import com.ayun.model.vo.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 登录日志。写入侧只在 {@code AuthService.login()} 的四个出口各被调用一次，读取侧只服务后台「日志管理 / 登录日志」页签
 */
@Slf4j
@Service
public class LoginLogService {

    /**
     * {@code login_logs.user_agent} 是 VARCHAR(255)（db-design.md §1.8）。真实浏览器不申报这么长，
     * 但伪造的长串会让整条 INSERT 在严格模式下报错、连带丢掉这一行，所以按列宽截断而不是让它失败
     */
    private static final int USER_AGENT_MAX_LENGTH = 255;

    /**
     * 攻击 Top 榜的条数
     */
    private static final int TOP_LIMIT = 10;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private SettingsService settingsService;

    /**
     * 异步落一行。原始地址与 UA 必须由调用方在**请求线程**上取好传进来：
     * {@code @Async} 换了线程，{@code RequestContextHolder} 里的请求就不在了
     * @param userId 仅登录成功时非空，其余三个出口传 null
     * @param result {@link LoginLog} 的四个 RESULT_* 常量之一，由调用方按它所在的那个出口给定
     */
    @Async
    public void recordLogin(String email, Long userId, String ip, String userAgent, String result) {
        try {
            loginLogMapper.insert(LoginLog.builder()
                    .email(email)
                    .userId(userId)
                    .ip(ip)
                    .userAgent(truncate(userAgent))
                    .result(result)
                    .build());
        } catch (Exception e) {
            // 日志是旁路：写失败只留一条 error，绝不让登录接口跟着失败，也不因此改口提示。
            // 只记事件不记邮箱，与全局异常处理器不记冲突值的口径一致
            log.error("登录日志写入失败：result={} 原因={}", result, e.getMessage(), e);
        }
    }

    /**
     * 概览：当前锁定策略 + 三张计数卡 + 失败来源 Top 榜，一次请求出齐，卡片与榜单出自同一份数据可对账
     */
    public LoginOverviewVO getOverview() {
        List<LoginOverviewVO.TopAttack> topAttacks =
                loginLogMapper.countBySource(TOP_LIMIT).stream().map(LoginLogService::toTopAttack).toList();
        return LoginOverviewVO.builder()
                .policy(settingsService.getLoginPolicy())
                .todayFailures(loginLogMapper.countTodayFailures())
                .todayLocked(loginLogMapper.countTodayLocked())
                .recentFailureSources(loginLogMapper.countRecentFailureSources())
                .topAttacks(topAttacks)
                .build();
    }

    /**
     * 明细分页。时间范围、邮箱、来源地址前缀、结果四个筛选可任意组合；
     * 与访问日志那侧不同的一点：时间范围留空就是**不加时间条件**（保留期内全量），而不是默认当日
     */
    public PageResult<LoginLogVO> getLogs(AdminLoginLogQueryDTO queryDTO) {
        LocalDateTime start = startOf(queryDTO.getStartDate());
        LocalDateTime end = exclusiveEndOf(queryDTO.getEndDate());
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BizException("结束日期早于开始日期");
        }
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<LoginLogVO.LoginRow> rows = loginLogMapper.findAdminList(
                start, end, queryDTO.getEmail(),
                IpMaskUtils.likePattern(queryDTO.getIpPrefix()), queryDTO.getResult());
        PageInfo<LoginLogVO.LoginRow> pageInfo = new PageInfo<>(rows);
        List<LoginLogVO> list = rows.stream().map(LoginLogService::toItem).toList();
        return PageResult.of(pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize(), list);
    }

    /**
     * 一行原始记录换成一行出参：地址脱敏、UA 归类。原始地址在这一行之后就没有下一个去处了
     */
    private static LoginLogVO toItem(LoginLogVO.LoginRow row) {
        return LoginLogVO.builder()
                .id(row.getId())
                .createdAt(row.getCreatedAt())
                .email(row.getEmail())
                .userId(row.getUserId())
                .ipMasked(IpMaskUtils.mask(row.getIp()))
                .userAgentType(UserAgentClassifier.classify(row.getUserAgent()))
                .userAgent(row.getUserAgent())
                .result(row.getResult())
                .build();
    }

    private static LoginOverviewVO.TopAttack toTopAttack(LoginOverviewVO.AttackCount row) {
        return LoginOverviewVO.TopAttack.builder()
                .email(row.getEmail())
                .ipMasked(IpMaskUtils.mask(row.getIp()))
                .failures(row.getFailures())
                .build();
    }

    /**
     * 时间起（含当日零点）；留空为 null，即不加下界
     */
    private static LocalDateTime startOf(String date) {
        LocalDate parsed = parse(date, "开始");
        return parsed == null ? null : parsed.atStartOfDay();
    }

    /**
     * 时间止（止日期整天之后，不含）：口径与后台文章列表、访问日志一致——止日当天算全天
     */
    private static LocalDateTime exclusiveEndOf(String date) {
        LocalDate parsed = parse(date, "结束");
        return parsed == null ? null : parsed.plusDays(1).atStartOfDay();
    }

    private static LocalDate parse(String date, String label) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(label + "日期格式应为 yyyy-MM-dd");
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
