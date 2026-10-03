package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.IpUtils;
import com.ayun.dto.LoginRequest;
import com.ayun.dto.PasswordResetRequest;
import com.ayun.dto.RegisterRequest;
import com.ayun.entity.LoginLog;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.LoginPolicyVO;
import com.ayun.security.JwtUtils;
import com.ayun.security.TokenRevocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 注册链路的主键生成与校验顺序测试：用户 ID 与文章同策略，由服务层雪花 ID 显式赋值，不依赖数据库自增，
 * 唯一性冲突排在校验验证码之前；
 * 登录防爆破：两层计数——「邮箱 + 来源 IP」按策略阈值锁住单点穷举，只按邮箱的第二层（阈值 max(20, 策略×5)）
 * 堵住轮换来源地址的穷举，阈值与锁定时长都取自后台可调的锁定策略（未配置即 5 次 / 15 分钟）；
 * 登录的四种结局各写一条 login_logs，且只有成功那条带回用户 ID；
 * 登录成功后才留下「上次登录」记录；
 * 以及找回密码：发码前的账号存在性判断，与重置时只回写口令一列
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private LoginLogService loginLogService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    /** 重置口令要推失效水位；本类只关心写库与校验码，这里放个空桩避免注入为 null */
    @Mock
    private TokenRevocationService tokenRevocationService;

    /** 来源地址用真实实现（采信代理头那一档），几处「经代理的尝试归到同一个键」的用例靠它算键位 */
    @Spy
    private IpUtils ipUtils = new IpUtils(true);

    @InjectMocks
    private AuthService authService;

    private static final String LOGIN_EMAIL = "reader@ayun.com";

    /** 直连时的失败计数键：来源地址取自请求上下文，无 X-Forwarded-For 即用 remoteAddr */
    private static final String FAIL_KEY = "auth:login-fail:reader@ayun.com:127.0.0.1";

    /** 邮箱维度的第二层计数键：换来源地址绕不开它 */
    private static final String GLOBAL_FAIL_KEY = "auth:login-fail-global:reader@ayun.com";

    private RegisterRequest request(String email, String username) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword("secret123");
        request.setCode("123456");
        return request;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail(LOGIN_EMAIL);
        request.setPassword("secret123");
        return request;
    }

    /** IpUtils 只能从请求上下文取来源地址，登录用例必须先绑一个请求 */
    private void bindRequest(String forwardedFor) {
        bindRequest(forwardedFor, null);
    }

    private void bindRequest(String forwardedFor, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * login() 一进入就读一次锁定策略，不桩就是 null，碰阈值即 NPE。
     * 缺省这对值（5 次 / 15 分钟）就是策略键未配置时 SettingsService 回落的那份
     */
    private void stubPolicy(int maxFailures, int lockMinutes) {
        when(settingsService.getLoginPolicy()).thenReturn(LoginPolicyVO.builder()
                .maxFailures(maxFailures)
                .lockMinutes(lockMinutes)
                .build());
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("注册：落库的 User 带 19 位雪花 ID，且连续两枚不重复并递增")
    void registerAssignsSnowflakeId() {
        when(settingsService.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(verificationCodeService.verifyCode(anyString(), anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");

        authService.register(request("a@ayun.com", "readerA"));
        authService.register(request("b@ayun.com", "readerB"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(2)).insert(captor.capture());
        Long first = captor.getAllValues().get(0).getId();
        Long second = captor.getAllValues().get(1).getId();

        assertNotNull(first, "id 必须在服务层赋好，insert 语句里已有 id 列");
        assertTrue(first >= 1_000_000_000_000_000_000L, "雪花 ID 为 19 位");
        assertTrue(second > first, "连续注册的 ID 不得重复且随时间递增");
    }

    @Test
    @DisplayName("注册：不再走自增回填，find 冲突时不落库")
    void registerRejectsDuplicatedEmail() {
        when(settingsService.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(userMapper.findByEmail("a@ayun.com")).thenReturn(User.builder().id(1L).build());

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.register(request("a@ayun.com", "readerA")));

        assertEquals("该邮箱已被注册", error.getMessage());
        verify(userMapper, times(0)).insert(any());
    }

    @Test
    @DisplayName("邮箱或用户名已占用时不校验验证码：一次性码不该为一处输入冲突被烧掉")
    void uniquenessConflictDoesNotConsumeTheVerificationCode() {
        when(settingsService.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(userMapper.findByEmail("a@ayun.com")).thenReturn(User.builder().id(1L).build());

        org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.register(request("a@ayun.com", "readerA")));

        when(userMapper.findByEmail("c@ayun.com")).thenReturn(null);
        when(userMapper.findByUsername("readerA")).thenReturn(User.builder().id(2L).build());

        BizException usernameError = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.register(request("c@ayun.com", "readerA")));

        assertEquals("该用户名已被占用", usernameError.getMessage());
        verify(verificationCodeService, never()).verifyCode(anyString(), anyString());
        verify(userMapper, never()).insert(any());
    }

    // ---- 登录防爆破：auth:login-fail:{邮箱}:{来源 IP} 双维度计数 ----

    @Test
    @DisplayName("口令错误按「邮箱 + 来源 IP」计一次失败，并给键设 15 分钟窗口")
    void failedLoginIsCountedPerEmailAndIp() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(FAIL_KEY)).thenReturn(1L);
        when(valueOperations.increment(GLOBAL_FAIL_KEY)).thenReturn(1L);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest()));

        verify(valueOperations).increment(FAIL_KEY);
        verify(redisTemplate).expire(FAIL_KEY, 900L, TimeUnit.SECONDS);
        // 第二层不带来源地址，同一次失败也要计上，否则换 IP 就是清零
        verify(valueOperations).increment(GLOBAL_FAIL_KEY);
        verify(redisTemplate).expire(GLOBAL_FAIL_KEY, 900L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("策略改成 3 次 / 30 分钟：3 次即锁，窗口也按 30 分钟设")
    void lockThresholdAndWindowComeFromTheConfiguredPolicy() {
        bindRequest(null);
        stubPolicy(3, 30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(FAIL_KEY)).thenReturn(3L);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest()));

        // 计满阈值（3 次）就把窗口按配置的 30 分钟重新拉满
        verify(redisTemplate).expire(FAIL_KEY, 1800L, TimeUnit.SECONDS);

        when(valueOperations.get(FAIL_KEY)).thenReturn(3);
        when(redisTemplate.getExpire(FAIL_KEY, TimeUnit.SECONDS)).thenReturn(1800L);
        BizException locked = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.login(loginRequest()));
        assertEquals("尝试次数过多，请 30 分钟后再试", locked.getMessage());
    }

    @Test
    @DisplayName("经代理的多次尝试归到同一个键：来源地址取 X-Forwarded-For 首值")
    void failureKeyUsesFirstForwardedForValue() {
        bindRequest("203.0.113.9, 10.0.0.1");
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login-fail:reader@ayun.com:203.0.113.9")).thenReturn(2L);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest()));

        verify(valueOperations).increment("auth:login-fail:reader@ayun.com:203.0.113.9");
    }

    @Test
    @DisplayName("失败满阈值后直接拒绝，且不再校验口令——锁定期间不给穷举者任何反馈")
    void fifthFailureLocksFurtherAttempts() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FAIL_KEY)).thenReturn(5);
        // 刚锁上：剩余 TTL 就是整段锁定时长
        when(redisTemplate.getExpire(FAIL_KEY, TimeUnit.SECONDS)).thenReturn(900L);

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        assertEquals("尝试次数过多，请 15 分钟后再试", error.getMessage());
        verifyNoInteractions(authenticationManager);
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("锁定提示按计数键的剩余 TTL 报分钟数，向上取整到 1 分钟")
    void lockedMessageCountsDownTheRemainingLock() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FAIL_KEY)).thenReturn(9);
        when(redisTemplate.getExpire(FAIL_KEY, TimeUnit.SECONDS)).thenReturn(601L);

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        // 601 秒 = 10 分 1 秒，说「10 分钟」会让人以为再等 10 分钟就开得开
        assertEquals("尝试次数过多，请 11 分钟后再试", error.getMessage());
    }

    @Test
    @DisplayName("TTL 读不到时提示回落到配置的锁定时长，不报 0 分钟")
    void lockedMessageFallsBackToConfiguredMinutes() {
        bindRequest(null);
        stubPolicy(5, 30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FAIL_KEY)).thenReturn(5);
        when(redisTemplate.getExpire(FAIL_KEY, TimeUnit.SECONDS)).thenReturn(-2L);

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        assertEquals("尝试次数过多，请 30 分钟后再试", error.getMessage());
    }

    @Test
    @DisplayName("第 5 次失败把窗口重新拉满，锁定确实从锁上那刻起算 15 分钟")
    void lockingFailureRefreshesTheFullWindow() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(FAIL_KEY)).thenReturn(5L);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest()));

        verify(redisTemplate).expire(FAIL_KEY, 900L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("换一个来源地址就躲不开第二层：同一邮箱累计到全局阈值（max(20, 5×5)=25）即锁")
    void rotatingIpCannotOutrunTheEmailScopedThreshold() {
        // 攻击者这一轮换到另一个来源地址：按「邮箱 + 来源 IP」的键上还没有计数，只有邮箱层看得见累计
        bindRequest("198.51.100.7");
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:login-fail:reader@ayun.com:198.51.100.7")).thenReturn(0);
        when(valueOperations.get(GLOBAL_FAIL_KEY)).thenReturn(25);
        when(redisTemplate.getExpire(GLOBAL_FAIL_KEY, TimeUnit.SECONDS)).thenReturn(900L);

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        assertEquals("尝试次数过多，请 15 分钟后再试", error.getMessage());
        verifyNoInteractions(authenticationManager);
        verify(loginLogService).recordLogin(LOGIN_EMAIL, null, "198.51.100.7", null, LoginLog.RESULT_LOCKED);
    }

    @Test
    @DisplayName("登录成功清空两层失败计数，历史失败不牵连本来源")
    void successfulLoginClearsTheFailureCount() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userMapper.findByEmail(LOGIN_EMAIL)).thenReturn(User.builder()
                .id(7L).email(LOGIN_EMAIL).username("reader").role("user").build());
        when(jwtUtils.generateToken(LOGIN_EMAIL)).thenReturn("jwt");

        Map<String, Object> result = authService.login(loginRequest());

        assertEquals("jwt", result.get("token"));
        verify(redisTemplate).delete(FAIL_KEY);
        // 只清来源地址那层的话，换 IP 攒下的邮箱计数会一直压着下一次正常登录
        verify(redisTemplate).delete(GLOBAL_FAIL_KEY);
    }

    @Test
    @DisplayName("登录成功记下该用户与来源地址，经代理时记的是 X-Forwarded-For 首值而非代理地址")
    void successfulLoginRecordsTheClientIp() {
        bindRequest("203.0.113.9, 10.0.0.1");
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userMapper.findByEmail(LOGIN_EMAIL)).thenReturn(User.builder()
                .id(7L).email(LOGIN_EMAIL).username("reader").role("user").build());
        when(jwtUtils.generateToken(LOGIN_EMAIL)).thenReturn("jwt");

        authService.login(loginRequest());

        verify(userMapper).updateLastLogin(7L, "203.0.113.9");
    }

    @Test
    @DisplayName("口令错误不写上次登录：只有真正登录成功才留下这条记录")
    void failedLoginRecordsNoLastLogin() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(FAIL_KEY)).thenReturn(1L);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest()));

        verify(userMapper, never()).updateLastLogin(any(), anyString());
    }

    // ---- 四种结局各写一条 login_logs，只有成功那条带回用户 ID ----

    @Test
    @DisplayName("登录成功写 success 并带回用户 ID，来源地址与 UA 在请求线程取好后传出")
    void successfulLoginWritesOneSuccessRow() {
        bindRequest("203.0.113.9", "curl/8.4.0");
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userMapper.findByEmail(LOGIN_EMAIL)).thenReturn(User.builder()
                .id(7L).email(LOGIN_EMAIL).username("reader").role("user").build());
        when(jwtUtils.generateToken(LOGIN_EMAIL)).thenReturn("jwt");

        authService.login(loginRequest());

        verify(loginLogService).recordLogin(LOGIN_EMAIL, 7L, "203.0.113.9", "curl/8.4.0", LoginLog.RESULT_SUCCESS);
    }

    @Test
    @DisplayName("口令错写 wrong_password：账号不存在走的也是这一条出口，不区分二者")
    void wrongPasswordWritesOneRow() {
        bindRequest(null, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(FAIL_KEY)).thenReturn(1L);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest()));

        verify(loginLogService).recordLogin(LOGIN_EMAIL, null, "127.0.0.1",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", LoginLog.RESULT_WRONG_PASSWORD);
    }

    @Test
    @DisplayName("锁定期内的尝试写 locked，且不再消耗一次口令校验")
    void attemptWhileLockedWritesLockedRow() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FAIL_KEY)).thenReturn(5);
        when(redisTemplate.getExpire(FAIL_KEY, TimeUnit.SECONDS)).thenReturn(600L);

        org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        verify(loginLogService).recordLogin(LOGIN_EMAIL, null, "127.0.0.1", null, LoginLog.RESULT_LOCKED);
    }

    @Test
    @DisplayName("禁用账号写 disabled：不占爆破计数，异常与 401 提示照旧往外抛")
    void disabledAccountWritesDisabledRowAndIsNotCounted() {
        bindRequest(null);
        stubPolicy(5, 15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("账号已被禁用"));

        org.junit.jupiter.api.Assertions.assertThrows(DisabledException.class,
                () -> authService.login(loginRequest()));

        verify(loginLogService).recordLogin(LOGIN_EMAIL, null, "127.0.0.1", null, LoginLog.RESULT_DISABLED);
        verify(valueOperations, never()).increment(anyString());
        verify(userMapper, never()).updateLastLogin(any(), anyString());
    }

    // ---- 找回密码：发码前的账号存在性，与重置时只回写口令一列 ----

    private static final String RESET_EMAIL = "reader@ayun.com";

    private PasswordResetRequest resetRequest(String code, String newPassword) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(RESET_EMAIL);
        request.setCode(code);
        request.setNewPassword(newPassword);
        return request;
    }

    @Test
    @DisplayName("未注册的邮箱不发送重置码，不占用该邮箱的发码额度")
    void resetCodeIsNotMailedToUnknownEmail() {
        when(userMapper.findByEmail(RESET_EMAIL)).thenReturn(null);

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.sendPasswordResetCode(RESET_EMAIL));

        assertEquals("该邮箱尚未注册", error.getMessage());
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    @DisplayName("已注册的邮箱按重置用途发码")
    void resetCodeIsMailedForKnownEmail() {
        when(userMapper.findByEmail(RESET_EMAIL)).thenReturn(User.builder().id(7L).build());

        authService.sendPasswordResetCode(RESET_EMAIL);

        verify(verificationCodeService).sendPasswordResetCode(RESET_EMAIL);
    }

    @Test
    @DisplayName("重置只回写 id 与加密后的口令，其余列留 null 交给 <set> 跳过")
    void resetWritesOnlyThePasswordColumn() {
        when(userMapper.findByEmail(RESET_EMAIL)).thenReturn(User.builder()
                .id(7L).email(RESET_EMAIL).username("reader").avatar("http://x/a.png").build());
        when(verificationCodeService.verifyPasswordResetCode(RESET_EMAIL, "123456")).thenReturn(true);
        when(passwordEncoder.encode("ayun2026new")).thenReturn("ENC");

        authService.resetPassword(resetRequest("123456", "ayun2026new"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User written = captor.getValue();
        assertEquals(7L, written.getId());
        assertEquals("ENC", written.getPassword(), "落库的必须是散列值而非明文");
        assertNull(written.getEmail(), "带 email 会把整行原值回写，与改邮箱那条链路的口径一致");
        assertNull(written.getUsername());
        assertNull(written.getAvatar());
    }

    @Test
    @DisplayName("验证码不对时不改口令，也不触发一次无意义的 BCrypt 编码")
    void wrongResetCodeLeavesPasswordUntouched() {
        when(userMapper.findByEmail(RESET_EMAIL)).thenReturn(User.builder().id(7L).build());
        when(verificationCodeService.verifyPasswordResetCode(RESET_EMAIL, "000000")).thenReturn(false);

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> authService.resetPassword(resetRequest("000000", "ayun2026new")));

        assertEquals("验证码错误或已过期", error.getMessage());
        verify(userMapper, never()).update(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("账号不存在时不校验验证码：一次性码命中即销毁，不能为无账号的请求烧掉码")
    void resetForUnknownEmailSkipsCodeVerification() {
        when(userMapper.findByEmail(RESET_EMAIL)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class, () -> authService.resetPassword(resetRequest("123456", "ayun2026new")));

        verify(verificationCodeService, never()).verifyPasswordResetCode(anyString(), anyString());
        verify(userMapper, never()).update(any());
    }
}
