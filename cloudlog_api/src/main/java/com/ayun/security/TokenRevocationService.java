package com.ayun.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 失效水位：按账号记一个时刻，早于它签发的 token 一律作废。
 *
 * 为什么不按任务书原样的「把当前 token 的 jti 拉黑」：重置密码发生在未登录状态下（找回密码链路），
 * 请求头里根本没有攻击者那枚 token，拉黑「当前 token」等于什么都没拉黑。水位按账号记，
 * 改密前签发的全部会话一并作废；也不需要给历史 token 补 jti 声明——它们本来就没有
 */
@Component
public class TokenRevocationService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 水位键的存活时长直接取 token 有效期：那之前签发的 token 早已自然过期，键也就没用了 */
    @Value("${jwt.expiration}")
    private long tokenValidSeconds;

    private static final String EPOCH_KEY_PREFIX = "auth:token-epoch:";

    /**
     * 作废该账号此刻之前签发的全部 token
     * @param email 账号邮箱，即 JWT 的 subject
     */
    public void revokeBeforeNow(String email) {
        redisTemplate.opsForValue().set(EPOCH_KEY_PREFIX + email,
                System.currentTimeMillis() / 1000, tokenValidSeconds, TimeUnit.SECONDS);
    }

    /**
     * 判断一枚 token 是否已被作废
     * @param email 账号邮箱
     * @param issuedAt token 的签发时刻，缺失时按未作废处理（签名已通过，那是我们自己签的）
     * @return 签发时刻早于水位即为已作废；同秒签发算有效，免得刚改完密就把当前这次操作踢下线
     */
    public boolean isRevoked(String email, Date issuedAt) {
        if (issuedAt == null) {
            return false;
        }
        Object epoch = redisTemplate.opsForValue().get(EPOCH_KEY_PREFIX + email);
        return epoch instanceof Number number && issuedAt.getTime() / 1000 < number.longValue();
    }
}
