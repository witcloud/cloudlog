package com.ayun.service;

import com.ayun.common.ExcerptUtils;
import com.ayun.entity.Post;
import com.ayun.mapper.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存量摘要回填。摘要自 2026-10-03 起改为正文的服务端派生物（business-model.md §2.3），
 * 那之前发布的一批文章 {@code excerpt} 是空的，列表卡片那一行与详情页 description 都缺内容，
 * 所以启动时把对外可见且摘要为空的文章补上
 */
@Slf4j
@Component
public class ExcerptBackfillRunner implements CommandLineRunner {

    private final PostMapper postMapper;

    /** 开关默认开；置 false 即跳过回填，只让此后发布的文章带摘要 */
    private final boolean enabled;

    public ExcerptBackfillRunner(PostMapper postMapper,
                                 @Value("${app.backfill.excerpt:true}") boolean enabled) {
        this.postMapper = postMapper;
        this.enabled = enabled;
    }

    /**
     * 幂等靠 {@code findNeedingExcerpt} 的谓词本身：落库后该行不再命中，重复执行只是空转。
     * 正文里除代码与图片之外没有可读文字的（生成结果为空）跳过写入——写个空串进去，
     * 下一轮启动它又落回待处理集合，「跑完自动空转」就不成立了。
     * 整轮兜住异常：回填失败不该让应用起不来
     */
    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        try {
            List<Post> pending = postMapper.findNeedingExcerpt();
            int filled = 0;
            for (Post post : pending) {
                String excerpt = ExcerptUtils.generate(post.getContent(), ExcerptUtils.MAX_LENGTH);
                if (excerpt.isBlank()) {
                    continue;
                }
                postMapper.updateExcerpt(post.getId(), excerpt);
                filled++;
            }
            if (filled > 0) {
                log.info("摘要回填：{} 篇已发布或定时的文章补齐了摘要（本轮命中 {} 篇）", filled, pending.size());
            }
        } catch (Exception e) {
            log.error("摘要回填失败：{}", e.getMessage(), e);
        }
    }
}
