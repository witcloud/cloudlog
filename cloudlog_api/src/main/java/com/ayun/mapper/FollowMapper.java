package com.ayun.mapper;

import com.ayun.entity.Follow;
import com.ayun.model.vo.UserBriefVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户关注关系 Mapper 接口
 */
@Mapper
public interface FollowMapper {

    /**
     * 写入关注记录，唯一键 uk_author_follower 冲突时忽略
     */
    int insert(Follow follow);

    /**
     * 判断关注者是否已关注该作者
     */
    boolean existsByAuthorAndFollower(@Param("authorId") Long authorId, @Param("followerId") Long followerId);

    /**
     * 按 (author_id, follower_id) 删除关注记录
     */
    int delete(@Param("authorId") Long authorId, @Param("followerId") Long followerId);

    /**
     * 统计某用户的被关注数
     */
    int countByAuthor(@Param("authorId") Long authorId);

    /**
     * 统计某用户关注了多少人
     */
    int countByFollower(@Param("followerId") Long followerId);

    /**
     * 列出「我关注的」用户，按关注时刻倒序
     */
    List<UserBriefVO> findFollowing(@Param("followerId") Long followerId);

    /**
     * 列出「关注我」的用户，按关注时刻倒序
     */
    List<UserBriefVO> findFollowers(@Param("authorId") Long authorId);

    /**
     * 在给定用户中筛出「我关注了谁」，用于作者主页两栏一次标注关注状态，避免逐行 EXISTS
     */
    List<Long> findFollowedAmong(@Param("followerId") Long viewerId, @Param("ids") List<Long> ids);

    /**
     * 在给定用户中筛出「谁关注了我」，与 findFollowedAmong 同时命中即为互相关注
     */
    List<Long> findFollowingMeAmong(@Param("authorId") Long viewerId, @Param("ids") List<Long> ids);
}
