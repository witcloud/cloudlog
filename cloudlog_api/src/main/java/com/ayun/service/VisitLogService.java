package com.ayun.service;

import com.ayun.common.UserAgentClassifier;
import com.ayun.entity.VisitLog;
import com.ayun.mapper.VisitLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 访问日志写入侧。只在文章详情命中阅读量判定时被调用一次，
 * 因此一次访问产生的 visit_logs 行与 view_count 的那一次 +1 同源同判据（business-model.md §5.6）
 */
@Slf4j
@Service
public class VisitLogService {

    /**
     * {@code visit_logs.user_agent} 是 VARCHAR(255)（db-design.md §1.8）。真实浏览器不申报这么长，
     * 但伪造的长串会让整条 INSERT 在严格模式下报错、连带丢掉这一行，所以按列宽截断而不是让它失败
     */
    private static final int USER_AGENT_MAX_LENGTH = 255;

    @Autowired
    private VisitLogMapper visitLogMapper;

    /**
     * 异步落一行。原始地址与 UA 必须由调用方在**请求线程**上取好传进来：
     * {@code @Async} 换了线程，{@code RequestContextHolder} 里的请求就不在了
     * @param postId 被访问的文章 ID（只在已发布文章的详情上调用，故恒非空）
     * @param ip 客户端原始地址，不脱敏入库，脱敏只发生在读取侧
     * @param userAgent 原始 UA，可为空
     */
    @Async
    public void recordVisit(Long postId, String ip, String userAgent) {
        if (UserAgentClassifier.isBot(userAgent)) {
            return;
        }
        try {
            visitLogMapper.insert(VisitLog.builder()
                    .postId(postId)
                    .ip(ip)
                    .userAgent(truncate(userAgent))
                    .build());
        } catch (Exception e) {
            // 日志是旁路：写失败只留一条 error，绝不让详情接口跟着失败
            log.error("访问日志写入失败：postId={} 原因={}", postId, e.getMessage(), e);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
