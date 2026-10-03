package com.ayun.service;

import com.ayun.common.NotFoundException;
import com.ayun.entity.User;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.AuthorProfileVO;
import com.ayun.model.vo.PostVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 作者主页的公开读装配。统计与文章详情的作者卡共用 findAuthorStats 一条聚合，
 * 两处数字因此必然同口径，不在这里另写一份 status 谓词
 */
@Service
public class AuthorProfileService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private FollowService followService;

    /**
     * 按用户 ID 取作者主页视图。id 不存在（含已注销）时抛 NotFoundException，与关注接口同一句话、同一个 404
     * @param userId 作者用户 ID
     * @return 作者主页公开视图
     */
    public AuthorProfileVO getProfile(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在或已注销");
        }
        // 聚合行恒返回一行（无已发布文章时各项为 0），不必判空
        PostVO.AuthorVO stats = postMapper.findAuthorStats(userId);
        return AuthorProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .occupation(user.getOccupation())
                .followers(followService.countFollowers(userId))
                .following(followService.countFollowing(userId))
                .followed(followService.viewerFollowedAuthorOrNull(userId))
                .articleCount(stats.getArticleCount())
                .likeCount(stats.getLikeCount())
                .viewCount(stats.getViewCount())
                .build();
    }
}
