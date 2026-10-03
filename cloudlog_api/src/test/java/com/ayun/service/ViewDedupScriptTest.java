package com.ayun.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阅读去重脚本对真 Redis 跑一遍：Lua 只在服务端执行时才报语法错，mock 出来的绿灯不算验证过。
 * 键用一次性随机后缀，跑完删掉，不碰任何一篇文章的计数
 */
@SpringBootTest
class ViewDedupScriptTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final long WINDOW_SECONDS = 3600L;

    private final String dedupKey = "article:view_dedup:test-" + UUID.randomUUID();

    @AfterEach
    void removeTestKey() {
        redisTemplate.delete(dedupKey);
    }

    private Long runScript(String visitor) {
        return redisTemplate.execute(ArticleService.VIEW_DEDUP_SCRIPT, List.of(dedupKey), visitor, WINDOW_SECONDS);
    }

    @Test
    @DisplayName("首次访问：脚本返回新成员数 1，集合同时被带上 1 小时过期")
    void firstVisitAddsMemberAndSetsTtlInOneRoundTrip() {
        assertEquals(1L, runScript("ip:203.0.113.10"));

        Long ttl = redisTemplate.getExpire(dedupKey);
        assertNotNull(ttl, "键应当存在");
        assertTrue(ttl > 0 && ttl <= WINDOW_SECONDS,
                "入集合的那次调用就该把窗口一起设上，实际剩余 " + ttl + " 秒");
    }

    @Test
    @DisplayName("同一访问者再来：新成员数为 0，即本窗口内不再计数")
    void repeatVisitBySameVisitorAddsNothing() {
        runScript("ip:203.0.113.11");

        assertEquals(0L, runScript("ip:203.0.113.11"));
    }

    @Test
    @DisplayName("窗口不因新访客续期：第二个人进来时集合的剩余寿命只减不增")
    void newVisitorDoesNotExtendTheWindow() {
        runScript("ip:203.0.113.12");
        long ttlAfterFirst = redisTemplate.getExpire(dedupKey);

        assertEquals(1L, runScript("ip:203.0.113.13"));
        long ttlAfterSecond = redisTemplate.getExpire(dedupKey);

        assertTrue(ttlAfterSecond <= ttlAfterFirst,
                "持续流量下窗口必须照常关闭，否则先来的访客永久不再计数："
                        + ttlAfterFirst + " → " + ttlAfterSecond);
    }

    @Test
    @DisplayName("遗留的无过期时间旧键会被下一次访问补上 TTL，不再永久停摆")
    void orphanKeyWithoutTtlGetsRepaired() {
        // 复刻改造前的崩溃产物：SADD 成功但 EXPIRE 没来得及发出去，集合没有 TTL
        redisTemplate.opsForSet().add(dedupKey, "ip:203.0.113.14");
        long orphanTtl = redisTemplate.getExpire(dedupKey);
        assertEquals(-1L, orphanTtl, "先确认这个键此刻真没有过期时间");

        assertEquals(1L, runScript("ip:203.0.113.15"));

        long repairedTtl = redisTemplate.getExpire(dedupKey);
        assertTrue(repairedTtl > 0, "无 TTL 的遗留键应在下次访问时被补上窗口，实际 " + repairedTtl);
    }
}
