package com.ayun.service;

import com.ayun.common.NotFoundException;
import com.ayun.entity.User;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.AuthorProfileVO;
import com.ayun.model.vo.PostVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作者主页取人判据单元测试：禁用账号与「不存在」「已注销」同一个 404，且不再发起任何聚合查询
 */
@ExtendWith(MockitoExtension.class)
class AuthorProfileServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private FollowService followService;

    @InjectMocks
    private AuthorProfileService authorProfileService;

    @Test
    @DisplayName("正常账号：页头三格与关注数照常下发")
    void visibleAuthorGetsProfile() {
        when(userMapper.findVisibleById(1L)).thenReturn(
                User.builder().id(1L).username("tcloud").status(1).build());
        when(postMapper.findAuthorStats(1L)).thenReturn(
                PostVO.AuthorVO.builder().articleCount(7).viewCount(99).likeCount(3).build());
        when(followService.countFollowers(1L)).thenReturn(12);
        when(followService.countFollowing(1L)).thenReturn(5);
        when(followService.viewerFollowedAuthorOrNull(1L)).thenReturn(null);

        AuthorProfileVO profile = authorProfileService.getProfile(1L);

        assertEquals(7, profile.getArticleCount());
        assertEquals(99, profile.getViewCount());
        assertEquals(12, profile.getFollowers());
        assertNull(profile.getFollowed(), "匿名的关注态是 null 而不是 false，页面据此分清「没登录」");
    }

    @Test
    @DisplayName("禁用的人没有作者主页：与不存在同一个 404，且不发起统计与名单查询")
    void disabledAuthorIsNotFound() {
        when(userMapper.findVisibleById(7L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> authorProfileService.getProfile(7L));
        verify(postMapper, never()).findAuthorStats(any());
        verify(followService, never()).countFollowers(any());
    }
}
