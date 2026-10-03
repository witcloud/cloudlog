package com.ayun.service;

import com.ayun.mapper.PostPublishMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定时发布调度任务：到点把已过计划时刻的 scheduled 文章置为已发布
 */
@Slf4j
@Service
public class ScheduledPublishService {

    @Autowired
    private PostPublishMapper postPublishMapper;

    /**
     * 每分钟第 0 秒扫描一次。服务重启期间错过的到期文章由下一次扫描补发
     *
     * @return 本次置为已发布的文章数
     */
    @Scheduled(cron = "0 * * * * *")
    public int publishDueArticles() {
        int published = postPublishMapper.publishDueArticles();
        if (published > 0) {
            log.info("定时发布：{} 篇文章已到计划时刻并置为已发布", published);
        }
        return published;
    }
}
