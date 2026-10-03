package com.ayun.security;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 失效水位的读写边界：写进去的时刻必须与 token 有效期同寿，
 * 判定时只压「水位之前」签发的 token，同秒与之后的都必须放行
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private TokenRevocationService tokenRevocationService;

    private static final String EMAIL = "reader@ayun.com";
    private static final String EPOCH_KEY = "auth:token-epoch:reader@ayun.com";
    private static final long TOKEN_VALID_SECONDS = 604800L;

    @BeforeEach
    void injectTokenLifetime() {
        ReflectionTestUtils.setField(tokenRevocationService, "tokenValidSeconds", TOKEN_VALID_SECONDS);
    }

    @Test
    @DisplayName("置水位：写入当前秒级时刻，键活到 token 自然过期为止")
    void revokeWritesCurrentEpochWithTokenLifetimeTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        long floor = System.currentTimeMillis() / 1000;

        tokenRevocationService.revokeBeforeNow(EMAIL);

        ArgumentCaptor<Object> written = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(eq(EPOCH_KEY), written.capture(), eq(TOKEN_VALID_SECONDS), eq(TimeUnit.SECONDS));
        long epoch = ((Number) written.getValue()).longValue();
        assertTrue(epoch >= floor && epoch <= System.currentTimeMillis() / 1000 + 1,
                "水位应是此刻的秒级时间戳，实际写入 " + epoch);
    }

    @Test
    @DisplayName("水位之前签发的 token 被判定已作废")
    void tokenIssuedBeforeEpochIsRevoked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EPOCH_KEY)).thenReturn(2000L);

        assertTrue(tokenRevocationService.isRevoked(EMAIL, new Date(1000_000)));
    }

    @Test
    @DisplayName("水位之后与同秒签发的 token 都仍可用：刚改完密不能把自己踢下线")
    void tokenAtOrAfterEpochSurvives() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EPOCH_KEY)).thenReturn(2000L);

        assertFalse(tokenRevocationService.isRevoked(EMAIL, new Date(2000_000)), "同秒签发应放行");
        assertFalse(tokenRevocationService.isRevoked(EMAIL, new Date(3000_000)), "改密后新签发的 token 必须可用");
    }

    @Test
    @DisplayName("从没改过密的账号一律放行")
    void accountWithoutEpochIsNeverRevoked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EPOCH_KEY)).thenReturn(null);

        assertFalse(tokenRevocationService.isRevoked(EMAIL, new Date(1000_000)));
    }

    @Test
    @DisplayName("token 缺签发时刻时不去读水位，也不判为作废")
    void missingIssuedAtShortCircuits() {
        assertFalse(tokenRevocationService.isRevoked(EMAIL, null));

        verify(redisTemplate, never()).opsForValue();
    }
}
