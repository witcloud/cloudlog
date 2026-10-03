package com.ayun.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码服务单元测试：覆盖注册、改邮箱与找回密码三个场景的键位隔离、一次性验证码的销毁语义，以及发码前置频控
 */
@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private EmailService emailService;

    @Mock
    private com.ayun.common.IpUtils ipUtils;

    @InjectMocks
    private VerificationCodeService verificationCodeService;

    private static final String EMAIL = "reader@ayun.com";

    /** 频控默认放行，让存量用例只断言键位与一次性语义 */
    @BeforeEach
    void allowSendingByDefault() {
        lenient().when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
        // 邮箱层额度之外还有来源地址与全站两层日额度，取值只影响键名不影响语义
        lenient().when(ipUtils.clientIp()).thenReturn("203.0.113.9");
    }

    /** 取出第 index 次 set 写入的键。verify 必须按总次数下断言，capture 才会收齐全部调用。 */
    private String keyWrittenToRedis(int totalInvocations, int index) {
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(totalInvocations))
                .set(keys.capture(), any(), anyLong(), any(TimeUnit.class));
        return keys.getAllValues().get(index - 1);
    }

    /** 校验链路一次读两个键（错误计数键与验证码键），严格桩必须按键位分派，只 stub 其中一个会被顶掉 */
    private void stubStoredValues(Map<String, Object> byKey) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> byKey.get(invocation.getArgument(0, String.class)));
    }

    @Test
    @DisplayName("同一邮箱的注册码与改邮箱码落在不同键上，两条流程不互相覆盖")
    void registrationAndEmailChangeCodesDoNotShareKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        verificationCodeService.sendRegistrationCode(EMAIL);
        verificationCodeService.sendEmailChangeCode(EMAIL);

        String registrationKey = keyWrittenToRedis(2, 1);
        String emailChangeKey = keyWrittenToRedis(2, 2);
        assertNotEquals(registrationKey, emailChangeKey, "共用键位会让后发的码覆盖前一条流程");
        assertTrue(emailChangeKey.endsWith(EMAIL), "改邮箱的码应按新邮箱归档，便于按址校验");
    }

    @Test
    @DisplayName("改邮箱发码投递到新邮箱，且邮件文案不沿用注册措辞")
    void emailChangeCodeIsMailedToNewAddress() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        verificationCodeService.sendEmailChangeCode(EMAIL);

        ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjects = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleMail(recipients.capture(), subjects.capture(), anyString());
        assertEquals(EMAIL, recipients.getValue(), "验证码必须投递到待绑定的新邮箱");
        assertFalse(subjects.getValue().contains("注册"), "改邮箱的邮件不应写成注册验证码");
    }

    @Test
    @DisplayName("改邮箱码校验命中后立即销毁，同一验证码不可复用")
    void verifiedEmailChangeCodeIsConsumed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        verificationCodeService.sendEmailChangeCode(EMAIL);

        String key = keyWrittenToRedis(1, 1);
        ArgumentCaptor<Object> values = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(anyString(), values.capture(), anyLong(), any(TimeUnit.class));
        String issued = (String) values.getValue();
        assertEquals(6, issued.length(), "验证码应为 6 位数字");
        stubStoredValues(Map.of(key, issued));

        assertTrue(verificationCodeService.verifyEmailChangeCode(EMAIL, issued));
        verify(redisTemplate).delete(key);
    }

    @Test
    @DisplayName("改邮箱码不匹配时返回失败且不销毁，用户可在过期前重试")
    void wrongEmailChangeCodeLeavesStoredCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("123456");

        assertFalse(verificationCodeService.verifyEmailChangeCode(EMAIL, "000000"));
        verify(redisTemplate, never()).delete(anyString());
    }

    // ---- 发码频控（business-model.md §3.2「验证码与注册的防刷」）----

    @Test
    @DisplayName("60 秒窗口内第二次发码被拒，且验证码未生成、邮件未投递")
    void secondSendInsideWindowIsRejectedBeforeCodeIsGenerated() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> verificationCodeService.sendRegistrationCode(EMAIL));

        assertEquals("发送过于频繁，请稍后再试", error.getMessage());
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        verify(emailService, never()).sendSimpleMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("当日第 11 次发码被拒，提示与 60 秒限流区分开，前 10 次放行")
    void eleventhSendOfTheDayIsRejected() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(11L);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> verificationCodeService.sendRegistrationCode(EMAIL));
        assertEquals("今日发送次数已达上限，请明日再试", error.getMessage());
        verify(emailService, never()).sendSimpleMail(anyString(), anyString(), anyString());

        when(valueOperations.increment(anyString())).thenReturn(10L);
        verificationCodeService.sendRegistrationCode(EMAIL);
        verify(emailService).sendSimpleMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("频控按用途分键：注册与改邮箱各自计数，互不挤占额度")
    void rateLimitsAreScopedPerPurpose() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        verificationCodeService.sendRegistrationCode(EMAIL);
        verificationCodeService.sendEmailChangeCode(EMAIL);

        ArgumentCaptor<String> windowKeys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2))
                .setIfAbsent(windowKeys.capture(), any(), anyLong(), any(TimeUnit.class));
        List<String> windows = windowKeys.getAllValues();
        assertEquals("auth:code-limit:registration:" + EMAIL, windows.get(0),
                "窗口键应为 auth:code-limit:{purpose}:{email}");
        assertEquals("auth:code-limit:email-change:" + EMAIL, windows.get(1));

        ArgumentCaptor<String> dailyKeys = ArgumentCaptor.forClass(String.class);
        // 两次发码 ×（邮箱额度 + 来源地址额度 + 全站额度）三层日额度
        verify(valueOperations, times(6)).increment(dailyKeys.capture());
        assertEquals(2, (int) dailyKeys.getAllValues().stream()
                .filter(key -> key.startsWith("auth:code-daily:") && key.endsWith(EMAIL)).count());
    }

    @Test
    @DisplayName("当日额度键的 TTL 为正且不足一天，跨天自动归零")
    void dailyQuotaKeyExpiresAtMidnight() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        verificationCodeService.sendRegistrationCode(EMAIL);

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        // 邮箱、来源地址、全站三层日额度键都要设过期
        verify(redisTemplate, times(3)).expire(anyString(), ttl.capture(), any(TimeUnit.class));
        for (Long seconds : ttl.getAllValues()) {
            assertTrue(seconds > 0 && seconds <= 86400,
                    "TTL 应为距当日 24:00 的秒数，实际为 " + seconds);
        }
    }

    // ---- 防穷举：auth:code-try:{用途}:{邮箱} 计数，单周期错满 5 次即作废验证码 ----

    private static final String REGISTER_TRY_KEY = "auth:code-try:registration:" + EMAIL;
    private static final String REGISTER_CODE_KEY = "auth:code:" + EMAIL;
    private static final String CHANGE_TRY_KEY = "auth:code-try:email-change:" + EMAIL;
    private static final String CHANGE_CODE_KEY = "auth:code-change:" + EMAIL;

    @Test
    @DisplayName("首次错码给计数键设 5 分钟过期，窗口与验证码同长")
    void firstWrongTryOpensAFiveMinuteCountWindow() {
        stubStoredValues(Map.of(REGISTER_CODE_KEY, "123456"));
        when(valueOperations.increment(REGISTER_TRY_KEY)).thenReturn(1L);

        assertFalse(verificationCodeService.verifyCode(EMAIL, "000000"));

        verify(redisTemplate).expire(REGISTER_TRY_KEY, 5L, TimeUnit.MINUTES);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("错满第 5 次即删除验证码并提示重新获取，本周期不再给第 6 次机会")
    void fifthWrongTryInvalidatesTheCode() {
        stubStoredValues(Map.of(REGISTER_CODE_KEY, "123456"));
        when(valueOperations.increment(REGISTER_TRY_KEY)).thenReturn(5L);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> verificationCodeService.verifyCode(EMAIL, "000000"));

        assertEquals("验证码错误次数过多，请重新获取", error.getMessage());
        verify(redisTemplate).delete(REGISTER_CODE_KEY);
    }

    @Test
    @DisplayName("计数已满时即使输入正确验证码也不再放行")
    void exhaustedCounterRejectsEvenTheRightCode() {
        stubStoredValues(Map.of(REGISTER_TRY_KEY, 5, REGISTER_CODE_KEY, "123456"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> verificationCodeService.verifyCode(EMAIL, "123456"));

        assertEquals("验证码错误次数过多，请重新获取", error.getMessage());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("校验成功把错误计数键一起清掉，下一轮从 0 起算")
    void verifiedCodeAlsoClearsTheTryCounter() {
        stubStoredValues(Map.of(CHANGE_CODE_KEY, "123456"));

        assertTrue(verificationCodeService.verifyEmailChangeCode(EMAIL, "123456"));

        verify(redisTemplate).delete(CHANGE_CODE_KEY);
        verify(redisTemplate).delete(CHANGE_TRY_KEY);
    }

    @Test
    @DisplayName("错误计数按用途分键：注册码错满只拦注册校验，改邮箱码照旧可用")
    void tryCountersAreScopedPerPurpose() {
        stubStoredValues(Map.of(REGISTER_TRY_KEY, 5, REGISTER_CODE_KEY, "123456", CHANGE_CODE_KEY, "123456"));

        assertThrows(RuntimeException.class, () -> verificationCodeService.verifyCode(EMAIL, "123456"));
        assertTrue(verificationCodeService.verifyEmailChangeCode(EMAIL, "123456"));
    }

    @Test
    @DisplayName("没有验证码时不计失败，未发码的邮箱不会被预先扣光额度")
    void missingCodeCountsNoFailure() {
        stubStoredValues(Map.of());

        assertFalse(verificationCodeService.verifyCode(EMAIL, "000000"));

        verify(valueOperations, never()).increment(anyString());
    }

    // ---- 找回密码：auth:code-reset:{邮箱} 独立键位，与注册、改邮箱互不覆盖 ----

    private static final String RESET_CODE_KEY = "auth:code-reset:" + EMAIL;
    private static final String RESET_TRY_KEY = "auth:code-try:password-reset:" + EMAIL;

    @Test
    @DisplayName("三条链路的验证码各用各的键，后发的重置码不覆盖注册与改邮箱的码")
    void resetCodeUsesItsOwnKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        verificationCodeService.sendRegistrationCode(EMAIL);
        verificationCodeService.sendEmailChangeCode(EMAIL);
        verificationCodeService.sendPasswordResetCode(EMAIL);

        assertEquals(RESET_CODE_KEY, keyWrittenToRedis(3, 3), "重置码应落在 auth:code-reset: 键上");
    }

    @Test
    @DisplayName("重置码的发码频控按 password-reset 分键，不借用其它用途的当日额度")
    void resetSendQuotaIsScopedToItsOwnPurpose() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        verificationCodeService.sendPasswordResetCode(EMAIL);

        ArgumentCaptor<String> windows = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(windows.capture(), any(), anyLong(), any(TimeUnit.class));
        assertEquals("auth:code-limit:password-reset:" + EMAIL, windows.getValue());
    }

    @Test
    @DisplayName("重置码邮件写明重置用途，正文不借用注册措辞")
    void resetCodeMailStatesItsOwnPurpose() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        verificationCodeService.sendPasswordResetCode(EMAIL);

        ArgumentCaptor<String> subjects = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleMail(anyString(), subjects.capture(), bodies.capture());
        assertTrue(subjects.getValue().contains("重置密码"));
        assertFalse(bodies.getValue().contains("注册"), "正文不能沿用注册验证码的措辞");
    }

    @Test
    @DisplayName("重置码命中后连同错误计数一起销毁，同一码不可复用")
    void verifiedResetCodeIsConsumed() {
        stubStoredValues(Map.of(RESET_CODE_KEY, "123456"));

        assertTrue(verificationCodeService.verifyPasswordResetCode(EMAIL, "123456"));

        verify(redisTemplate).delete(RESET_CODE_KEY);
        verify(redisTemplate).delete(RESET_TRY_KEY);
    }

    @Test
    @DisplayName("重置码错满 5 次只作废重置这一条链路，注册码照旧可用")
    void exhaustedResetTriesDoNotTouchTheRegistrationChain() {
        stubStoredValues(Map.of(RESET_CODE_KEY, "123456", RESET_TRY_KEY, 5, REGISTER_CODE_KEY, "123456"));

        assertThrows(RuntimeException.class,
                () -> verificationCodeService.verifyPasswordResetCode(EMAIL, "123456"));
        assertTrue(verificationCodeService.verifyCode(EMAIL, "123456"));
    }
}
