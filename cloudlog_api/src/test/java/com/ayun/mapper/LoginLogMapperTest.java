package com.ayun.mapper;

import com.ayun.common.IpMaskUtils;
import com.ayun.entity.LoginLog;
import com.ayun.model.vo.LoginLogVO;
import com.ayun.model.vo.LoginOverviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * login_logs 的 SQL 契约：四种结果都存得下、三张计数卡的谓词边界、Top 榜的组合键聚合、
 * 四个可任意组合的筛选（含脱敏前缀回搜原始地址）、以及保留期删除的谓词边界。
 * 这些都写在 XML 里，mock 出不来，只能对真库跑。
 *
 * <p>用例数据只用 {@code probe-*.login-log@invalid.test} 这一组合成邮箱与 {@code 240.0.0.0/8}
 * （保留段，真实访客不可能出现在这个前缀上）。整类跑在一个会回滚的事务里：其中一条用例会执行保留期
 * 那条范围 DELETE，回滚保证它不碰到别人的行。计数卡断言一律取插入前的基线做差，库里已有的真实行不参与判断。
 *
 * <p>三级缓存被降到 STATEMENT：MyBatis 的一级缓存按 SqlSession 生效，测试事务里同一条无参计数语句
 * 调第二次会直接命中第一次的结果，读不到自己刚插进去的行。生产环境每个请求自带一个新会话，不存在这个滞后
 */
@SpringBootTest(properties = "mybatis.configuration.local-cache-scope=statement")
@Transactional
class LoginLogMapperTest {

    private static final String EMAIL_A = "probe-a.login-log@invalid.test";

    private static final String EMAIL_B = "probe-b.login-log@invalid.test";

    /** 两个地址分属不同的 /16，脱敏后是两格，前缀筛选也就能分开 */
    private static final String IP_ONE = "240.0.113.1";

    private static final String IP_TWO = "240.1.7.9";

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void insert(String email, Long userId, String ip, String userAgent, String result, LocalDateTime at) {
        jdbcTemplate.update("INSERT INTO login_logs (email, user_id, ip, user_agent, result, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", email, userId, ip, userAgent, result, at);
    }

    @Test
    @DisplayName("四种结果各存一行，只有成功那行带回用户 ID")
    void allFourResultsAreStoredAndOnlySuccessCarriesUserId() {
        LocalDateTime now = LocalDateTime.now();
        insert(EMAIL_A, 7L, IP_ONE, "Mozilla/5.0 (Macintosh) Chrome/120.0", LoginLog.RESULT_SUCCESS, now);
        insert(EMAIL_A, null, IP_ONE, "curl/8.4.0", LoginLog.RESULT_WRONG_PASSWORD, now);
        insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_LOCKED, now);
        insert(EMAIL_A, null, IP_ONE, "Mozilla/5.0", LoginLog.RESULT_DISABLED, now);

        List<LoginLogVO.LoginRow> rows = loginLogMapper.findAdminList(
                now.minusMinutes(1), now.plusMinutes(1), EMAIL_A, null, null);

        assertEquals(4, rows.size());
        LoginLogVO.LoginRow success = rows.stream()
                .filter(row -> LoginLog.RESULT_SUCCESS.equals(row.getResult())).findFirst().orElseThrow();
        assertEquals(7L, success.getUserId().longValue());
        assertEquals(3, rows.stream().filter(row -> row.getUserId() == null).count(), "三种失败都不该有用户 ID");
        assertNull(rows.stream().filter(row -> LoginLog.RESULT_LOCKED.equals(row.getResult()))
                .findFirst().orElseThrow().getUserAgent(), "未申报 UA 就存 null，不编一个");
    }

    @Test
    @DisplayName("三张计数卡与明细对得上：今日失败不含成功、今日锁定只数 locked、来源按邮箱+地址去重")
    void overviewCardsReconcileWithTheRows() {
        LocalDateTime now = LocalDateTime.now();
        int baseFailures = loginLogMapper.countTodayFailures();
        int baseLocked = loginLogMapper.countTodayLocked();
        int baseSources = loginLogMapper.countRecentFailureSources();

        insert(EMAIL_A, 7L, IP_ONE, null, LoginLog.RESULT_SUCCESS, now);
        insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_WRONG_PASSWORD, now);
        insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_LOCKED, now);
        insert(EMAIL_B, null, IP_TWO, null, LoginLog.RESULT_DISABLED, now);
        // 同一（邮箱, 地址）的第二次失败：算来源时不另开一格
        insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_WRONG_PASSWORD, now);

        assertEquals(baseFailures + 4, loginLogMapper.countTodayFailures(), "成功那一行不计入失败");
        assertEquals(baseLocked + 1, loginLogMapper.countTodayLocked());
        // 四行失败但只有两个组合键：A@IP_ONE 与 B@IP_TWO
        assertEquals(baseSources + 2, loginLogMapper.countRecentFailureSources());
    }

    @Test
    @DisplayName("近一小时的窗口不把一小时前的尝试算进来")
    void recentSourceWindowExcludesOlderRows() {
        int baseSources = loginLogMapper.countRecentFailureSources();
        insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_WRONG_PASSWORD, LocalDateTime.now().minusMinutes(61));

        assertEquals(baseSources, loginLogMapper.countRecentFailureSources());
    }

    @Test
    @DisplayName("Top 榜按「邮箱 + 地址」组合聚合，同邮箱的两个地址分列两行")
    void topAttacksGroupByTheEmailIpPair() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 3; i++) {
            insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_WRONG_PASSWORD, now);
        }
        insert(EMAIL_A, null, IP_TWO, null, LoginLog.RESULT_LOCKED, now);
        insert(EMAIL_B, null, IP_ONE, null, LoginLog.RESULT_WRONG_PASSWORD, now);
        // 成功那一行不进榜：这一榜回答的是「谁在被打」
        insert(EMAIL_B, 8L, IP_ONE, null, LoginLog.RESULT_SUCCESS, now);

        List<LoginOverviewVO.AttackCount> mine = loginLogMapper.countBySource(10).stream()
                .filter(row -> row.getEmail().startsWith("probe-")).toList();

        assertEquals(3, mine.size(), "两个邮箱共三个不同的组合键");
        LoginOverviewVO.AttackCount leader = mine.get(0);
        assertEquals(EMAIL_A, leader.getEmail());
        assertEquals(IP_ONE, leader.getIp(), "原始地址只出现在这一行，出参前必须过脱敏");
        assertEquals(3, leader.getFailures().intValue());
        assertTrue(mine.get(1).getFailures() <= leader.getFailures(), "按失败次数倒序");
    }

    @Test
    @DisplayName("四个筛选可任意组合，照抄屏幕上的脱敏前缀就能回搜到原始地址")
    void filtersCombine() {
        LocalDateTime now = LocalDateTime.now();
        insert(EMAIL_A, 7L, IP_ONE, "Mozilla/5.0 (iPhone) CriOS/120", LoginLog.RESULT_SUCCESS, now);
        insert(EMAIL_A, null, IP_TWO, "python-requests/2.31", LoginLog.RESULT_WRONG_PASSWORD, now);
        insert(EMAIL_B, null, IP_ONE, "Mozilla/5.0 (Windows NT 10.0) Edge/120", LoginLog.RESULT_LOCKED, now);
        // 落在昨天：时间范围一收紧就该掉出去
        insert(EMAIL_B, null, IP_ONE, null, LoginLog.RESULT_DISABLED, now.minusDays(1));

        assertEquals(3, loginLogMapper.findAdminList(
                now.toLocalDate().atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay(),
                "probe-", null, null).size());

        // 邮箱按尝试时输入的原样串筛，账号不存在的那些也一样搜得到
        assertEquals(2, loginLogMapper.findAdminList(null, null, EMAIL_A, null, null).size());

        List<LoginLogVO.LoginRow> byMaskedPrefix = loginLogMapper.findAdminList(
                null, null, null, IpMaskUtils.likePattern("240.0.*.*"), LoginLog.RESULT_LOCKED);
        assertEquals(1, byMaskedPrefix.size());
        assertEquals(EMAIL_B, byMaskedPrefix.get(0).getEmail());

        List<LoginLogVO.LoginRow> byEmailAndPrefix = loginLogMapper.findAdminList(
                null, null, "probe-a", IpMaskUtils.likePattern("240.1"), null);
        assertEquals(1, byEmailAndPrefix.size());
        assertEquals(LoginLog.RESULT_WRONG_PASSWORD, byEmailAndPrefix.get(0).getResult());

        // 比屏幕上那一格更细的前缀照样能用：同一个时间窗里，240.0.113.% 把 240.1.7.9 那一行筛了出去
        assertEquals(2, loginLogMapper.findAdminList(
                now.toLocalDate().atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay(),
                null, IpMaskUtils.likePattern("240.0.113"), null).size());
    }

    @Test
    @DisplayName("保留期删除只碰早于阈值的行，恰好等于阈值的那行留着")
    void retentionDeleteIsBoundaryExact() {
        // 列是 TIMESTAMP(0)，带小数的阈值会因四舍五入把边界那一行算进「早于」里，先抹到整秒
        LocalDateTime threshold = LocalDateTime.now().minusDays(180).withNano(0);
        insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_WRONG_PASSWORD, threshold.minusSeconds(1));
        insert(EMAIL_A, null, IP_ONE, null, LoginLog.RESULT_WRONG_PASSWORD, threshold);

        loginLogMapper.deleteCreatedBefore(threshold, 5000);

        List<LoginLogVO.LoginRow> left = loginLogMapper.findAdminList(null, null, EMAIL_A, null, null);
        assertEquals(1, left.size(), "恰好等于阈值的行不属于「早于」");
        assertTrue(!left.get(0).getCreatedAt().isBefore(threshold));
    }
}
