package com.ayun.service;

import cn.hutool.core.util.RandomUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
    private static final long CODE_EXPIRE_MINUTES = 5;

    /**
     * 发送注册验证码
     * @param email 用户邮箱
     */
    public void sendRegistrationCode(String email) {
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
     * 校验验证码
     * @param email 用户邮箱
     * @param code 待校验的验证码
     * @return 是否校验通过
     */
    public boolean verifyCode(String email, String code) {
        String savedCode = (String) redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email);
        if (savedCode != null && savedCode.equals(code)) {
            // 校验成功后删除验证码，防止重复使用
            redisTemplate.delete(CODE_KEY_PREFIX + email);
            return true;
        }
        return false;
    }
}
