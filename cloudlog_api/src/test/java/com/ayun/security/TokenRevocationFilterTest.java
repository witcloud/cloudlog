package com.ayun.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 改密后旧 token 是否真的失效，只能沿完整过滤链验：Mapper→过滤器→401 才算证据，
 * 单测里那句 isRevoked 为真不代表过滤器读得到。账号用测试读者 alex@example.com（不是本机作者号），
 * 用例结束立刻删掉水位键，避免把谁的会话留在失效状态
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenRevocationFilterTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 本地库里 id=3 的普通读者账号 */
    private static final String EMAIL = "alex@example.com";

    private static final String EPOCH_KEY = "auth:token-epoch:" + EMAIL;

    /** 只登录可见的接口：未认证时安全链返回 401，前端据此清本地 token */
    private static final String PROTECTED_GET = "/articles/mine";

    @AfterEach
    void clearEpoch() {
        redisTemplate.delete(EPOCH_KEY);
    }

    @Test
    @DisplayName("带有效 token 能读登录可见的接口")
    void validTokenAuthenticates() throws Exception {
        String jwt = jwtUtils.generateToken(EMAIL);

        mvc.perform(get(PROTECTED_GET).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("水位晚于 token 的签发时刻时，同一枚 token 被过滤链拒到 401")
    void tokenIssuedBeforeEpochIsRejected() throws Exception {
        String jwt = jwtUtils.generateToken(EMAIL);
        // 手写水位而不走 revokeBeforeNow：JWT 的 iat 只有秒级精度，同秒内签发与置水位本不可分辨
        // （那条规则由 TokenRevocationServiceTest 覆盖）。这里要验的是过滤链读得到水位并拒绝，
        // 真实场景就是改密发生在签发之后若干秒
        redisTemplate.opsForValue().set(EPOCH_KEY, System.currentTimeMillis() / 1000 + 1, 60, TimeUnit.SECONDS);

        mvc.perform(get(PROTECTED_GET).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登录状态已失效，请重新登录"));
    }

    @Test
    @DisplayName("水位之后再签发的 token 照常可用：失效只压过去，不影响新会话")
    void tokenIssuedAfterEpochIsAccepted() throws Exception {
        tokenRevocationService.revokeBeforeNow(EMAIL);
        // 水位是秒级时刻，同秒签发的 token 也必须放行，否则改密当下就会把这次操作用完的 token 废掉
        String freshJwt = jwtUtils.generateToken(EMAIL);

        mvc.perform(get(PROTECTED_GET).header("Authorization", "Bearer " + freshJwt))
                .andExpect(status().isOk());
    }
}
