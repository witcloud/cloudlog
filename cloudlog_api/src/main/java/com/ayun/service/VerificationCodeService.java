package com.ayun.service;

import cn.hutool.core.util.RandomUtil;
import com.ayun.common.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 验证码业务逻辑类
 */
@Service
public class VerificationCodeService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private EmailService emailService;

    private static final String CODE_KEY_PREFIX = "auth:code:";
    private static final String EMAIL_CHANGE_CODE_KEY_PREFIX = "auth:code-change:";
    private static final String PASSWORD_RESET_CODE_KEY_PREFIX = "auth:code-reset:";
    private static final long CODE_EXPIRE_MINUTES = 5;

    private static final String SEND_INTERVAL_KEY_PREFIX = "auth:code-limit:";
    private static final String SEND_DAILY_KEY_PREFIX = "auth:code-daily:";
    private static final String CODE_TRY_KEY_PREFIX = "auth:code-try:";
    private static final String PURPOSE_REGISTRATION = "registration";
    private static final String PURPOSE_EMAIL_CHANGE = "email-change";
    private static final String PURPOSE_PASSWORD_RESET = "password-reset";
    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final long SEND_DAILY_LIMIT = 10;
    private static final String TOO_FREQUENTLY = "发送过于频繁，请稍后再试";
    /** 当日额度用完与 60 秒限流是两件事：前者今天怎么做都不行，后者等一分钟就好，提示不能复用一句 */
    private static final String DAILY_LIMIT_REACHED = "今日发送次数已达上限，请明日再试";

    /** 单个验证码周期内允许的校验失败次数，计满即作废该验证码 */
    private static final long MAX_CODE_TRIES = 5L;
    private static final String TOO_MANY_CODE_TRIES = "验证码错误次数过多，请重新获取";

    /**
     * 发码前置频控：60 秒窗口与当日额度都按「用途 + 邮箱」分键，互不影响。
     * 必须排在生成验证码之前，被限流时既不烧验证码也不消耗邮件（business-model.md §3.2「验证码与注册的防刷」）
     */
    private void checkSendAllowed(String purpose, String email) {
        String scoped = purpose + ":" + email;
        Boolean firstInWindow = redisTemplate.opsForValue()
                .setIfAbsent(SEND_INTERVAL_KEY_PREFIX + scoped, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstInWindow)) {
            throw new BizException(TOO_FREQUENTLY);
        }

        String dailyKey = SEND_DAILY_KEY_PREFIX + scoped;
        Long todayCount = redisTemplate.opsForValue().increment(dailyKey);
        if (todayCount != null && todayCount == 1L) {
            redisTemplate.expire(dailyKey, secondsUntilNextMidnight(), TimeUnit.SECONDS);
        }
        if (todayCount != null && todayCount > SEND_DAILY_LIMIT) {
            throw new BizException(DAILY_LIMIT_REACHED);
        }
    }

    /**
     * 当日额度键活到本地 24:00，跨天自然归零，不需要额外的重置任务
     */
    private long secondsUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
    }

    /**
     * 发送注册验证码
     * @param email 用户邮箱
     */
    public void sendRegistrationCode(String email) {
        checkSendAllowed(PURPOSE_REGISTRATION, email);

        // 生成 6 位数字验证码
        String code = RandomUtil.randomNumbers(6);
        
        // 存储到 Redis，设置 5 分钟过期
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        // 发送邮件
        String subject = "CloudLog 注册验证码";
        String content = "您的注册验证码为：" + code + "，有效期为 " + CODE_EXPIRE_MINUTES + " 分钟。请勿泄露给他人。";
        emailService.sendSimpleMail(email, subject, content);
    }

    /**
     * 校验注册验证码
     * @param email 用户邮箱
     * @param code 待校验的验证码
     * @return 是否校验通过
     */
    public boolean verifyCode(String email, String code) {
        return verify(PURPOSE_REGISTRATION, CODE_KEY_PREFIX, email, code);
    }

    /**
     * 发送修改邮箱验证码
     * @param newEmail 待绑定的新邮箱
     */
    public void sendEmailChangeCode(String newEmail) {
        checkSendAllowed(PURPOSE_EMAIL_CHANGE, newEmail);

        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(EMAIL_CHANGE_CODE_KEY_PREFIX + newEmail, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        String subject = "CloudLog 修改邮箱验证码";
        String content = "您的修改邮箱验证码为：" + code + "，有效期为 " + CODE_EXPIRE_MINUTES + " 分钟。若非本人操作，请忽略本邮件。";
        emailService.sendSimpleMail(newEmail, subject, content);
    }

    /**
     * 校验修改邮箱验证码
     * @param newEmail 待绑定的新邮箱
     * @param code 待校验的验证码
     * @return 是否校验通过
     */
    public boolean verifyEmailChangeCode(String newEmail, String code) {
        return verify(PURPOSE_EMAIL_CHANGE, EMAIL_CHANGE_CODE_KEY_PREFIX, newEmail, code);
    }

    /**
     * 发送重置密码验证码
     * @param email 账号邮箱
     */
    public void sendPasswordResetCode(String email) {
        checkSendAllowed(PURPOSE_PASSWORD_RESET, email);

        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(PASSWORD_RESET_CODE_KEY_PREFIX + email, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        String subject = "CloudLog 重置密码验证码";
        String content = "您的重置密码验证码为：" + code + "，有效期为 " + CODE_EXPIRE_MINUTES + " 分钟。若非本人操作，请忽略本邮件，你的密码不会被修改。";
        emailService.sendSimpleMail(email, subject, content);
    }

    /**
     * 校验重置密码验证码
     * @param email 账号邮箱
     * @param code 待校验的验证码
     * @return 是否校验通过
     */
    public boolean verifyPasswordResetCode(String email, String code) {
        return verify(PURPOSE_PASSWORD_RESET, PASSWORD_RESET_CODE_KEY_PREFIX, email, code);
    }

    /**
     * 三条校验链路共用的比对与防穷举计数，注册码、改邮箱码与重置码按用途各自计次，不互相挤占额度
     * @param purpose 用途，作错误计数的键位维度
     * @param codeKeyPrefix 该用途的验证码键前缀
     * @param email 邮箱
     * @param code 待校验的验证码
     * @return 是否校验通过
     */
    private boolean verify(String purpose, String codeKeyPrefix, String email, String code) {
        String codeKey = codeKeyPrefix + email;
        String tryKey = CODE_TRY_KEY_PREFIX + purpose + ":" + email;
        // 计数已满时本周期验证码已被作废，这里把原因说清楚，不复用「验证码错误或已过期」那套含糊说法
        if (codeTries(tryKey) >= MAX_CODE_TRIES) {
            throw new BizException(TOO_MANY_CODE_TRIES);
        }

        String savedCode = (String) redisTemplate.opsForValue().get(codeKey);
        if (savedCode != null && savedCode.equals(code)) {
            // 校验成功后删除验证码，防止重复使用；错误计数一并清零，不让上一轮的失败牵连下一个码
            redisTemplate.delete(codeKey);
            redisTemplate.delete(tryKey);
            return true;
        }
        if (savedCode == null) {
            // 没发过码或码已过期：没有可供穷举的目标，不记失败次数
            return false;
        }
        return rejectWrongCode(codeKey, tryKey);
    }

    /**
     * 错码计数：INCR 由 Redis 服务端完成；计满即删掉验证码，穷举者拿不到本周期第 6 次机会
     * @param codeKey 验证码键
     * @param tryKey 错误计数键
     * @return 恒为 false，仅在未达阈值时走到
     */
    private boolean rejectWrongCode(String codeKey, String tryKey) {
        Long tries = redisTemplate.opsForValue().increment(tryKey);
        if (tries != null && tries == 1L) {
            // 计数窗口与验证码同长，码一过期计数也随之归零
            redisTemplate.expire(tryKey, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
        if (tries != null && tries >= MAX_CODE_TRIES) {
            redisTemplate.delete(codeKey);
            throw new BizException(TOO_MANY_CODE_TRIES);
        }
        return false;
    }

    /**
     * 当前错误次数。计数由 INCR 写入，经值序列化器读回是数字；键不存在或非数字一律按 0 次
     */
    private long codeTries(String tryKey) {
        Object tries = redisTemplate.opsForValue().get(tryKey);
        return tries instanceof Number number ? number.longValue() : 0L;
    }
}
