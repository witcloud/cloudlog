package com.ayun.mapper;

import com.ayun.entity.Like;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文章点赞 Mapper 接口
 */
@Mapper
public interface LikeMapper {

    /**
     * 写入点赞记录，唯一键 uk_post_user 冲突时忽略
     */
    int insert(Like like);

    /**
     * 判断用户是否已点赞某文章
     */
    boolean existsByPostAndUser(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 按 (post_id, user_id) 删除点赞记录
     */
    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 删除文章时按其 post_id 清理全部点赞行
     */
    int deleteByPostId(@Param("postId") Long postId);

    /**
     * 统计某文章的点赞记录数
     */
    int countByPost(@Param("postId") Long postId);

    /**
     * 以 likes 表实际条数绝对重算并写回 posts.like_count
     */
    int recalculateLikeCount(@Param("postId") Long postId);
}
