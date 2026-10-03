package com.ayun.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 定时发布调度 Mapper：只承载「到期把 scheduled 翻成 published」这一条条件更新
 */
@Mapper
public interface PostPublishMapper {

    /**
     * 将已到期的定时发布文章批量置为已发布
     *
     * @return 被置为已发布的文章数；没有到期项时为 0，重复执行时也为 0
     */
    int publishDueArticles();
}
