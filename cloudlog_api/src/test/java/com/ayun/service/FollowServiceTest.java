package com.ayun.service;

import com.ayun.common.NotFoundException;
import com.ayun.entity.Follow;
import com.ayun.entity.User;
import com.ayun.mapper.FollowMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.FollowStateVO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.ProfileStatsVO;
import com.ayun.model.vo.UserBriefVO;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 关注服务单元测试：覆盖一人一关系的幂等语义、自关注拦截、目标存在性校验，以及作者主页两栏的关注标注
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FollowService followService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        // 分页方法里的 PageHelper.startPage 在 mock 的 Mapper 上不会被消费，不清会漏到下一个用例
        PageHelper.clearPage();
    }

    private void loginAsReader() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reader@ayun.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private User reader(Long id) {
        return User.builder().id(id).email("reader@ayun.com").build();
    }

    @Test
    @DisplayName("首次关注：以登录态身份写入关注关系，返回已关注状态与作者关注数")
    void followFirstTimeInsertsRecordAndReturnsState() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).build());
        when(followMapper.insert(any(Follow.class))).thenReturn(1);
        when(followMapper.existsByAuthorAndFollower(1L, 88L)).thenReturn(true);
        when(followMapper.countByAuthor(1L)).thenReturn(12);

        FollowStateVO state = followService.follow(1L);

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getAuthorId().intValue(), "author_id 取自路径参数");
        assertEquals(88, captor.getValue().getFollowerId().intValue(), "follower_id 应来自登录态");
        assertTrue(state.isFollowed());
        assertEquals(12, state.getFollowers().intValue());
    }

    @Test
    @DisplayName("重复关注幂等：唯一键冲突时不报错、不取消已有关系，仍返回已关注状态")
    void followTwiceIsIdempotent() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).build());
        when(followMapper.insert(any(Follow.class))).thenReturn(0);
        when(followMapper.existsByAuthorAndFollower(1L, 88L)).thenReturn(true);
        when(followMapper.countByAuthor(1L)).thenReturn(12);

        FollowStateVO state = followService.follow(1L);

        verify(followMapper, never()).delete(any(), any());
        assertTrue(state.isFollowed(), "重复关注不得翻转成未关注");
        assertEquals(12, state.getFollowers().intValue());
    }

    @Test
    @DisplayName("取消关注：按 (author_id, follower_id) 删除关系，返回未关注状态")
    void unfollowDeletesAndReturnsState() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).build());
        when(followMapper.existsByAuthorAndFollower(1L, 88L)).thenReturn(false);
        when(followMapper.countByAuthor(1L)).thenReturn(11);

        FollowStateVO state = followService.unfollow(1L);

        verify(followMapper).delete(1L, 88L);
        verify(followMapper, never()).insert(any(Follow.class));
        assertFalse(state.isFollowed());
        assertEquals(11, state.getFollowers().intValue());
    }

    @Test
    @DisplayName("取消未关注过的作者：删除影响 0 行，幂等返回未关注状态")
    void unfollowWithoutExistingRelationIsIdempotent() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).build());
        when(followMapper.existsByAuthorAndFollower(1L, 88L)).thenReturn(false);
        when(followMapper.countByAuthor(1L)).thenReturn(11);

        FollowStateVO state = followService.unfollow(1L);

        assertFalse(state.isFollowed());
        assertEquals(11, state.getFollowers().intValue());
    }

    @Test
    @DisplayName("关注自己：抛业务异常转 400，不落库")
    void followSelfThrowsBusinessError() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(userMapper.findById(88L)).thenReturn(User.builder().id(88L).build());

        RuntimeException error = assertThrows(RuntimeException.class, () -> followService.follow(88L));

        assertEquals("不能关注自己", error.getMessage());
        verify(followMapper, never()).insert(any(Follow.class));
    }

    @Test
    @DisplayName("关注不存在的用户：抛出未找到异常，不留孤儿关系")
    void followMissingUserThrowsNotFound() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(userMapper.findById(404L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> followService.follow(404L));

        verify(followMapper, never()).insert(any(Follow.class));
    }

    @Test
    @DisplayName("未登录浏览文章详情：不查库，关注状态恒为 false")
    void viewerStateForAnonymousSkipsLookup() {
        assertFalse(followService.viewerFollowedAuthor(1L));

        verifyNoInteractions(followMapper, userMapper);
    }

    @Test
    @DisplayName("作者本人浏览自己的文章：不查库，关注状态恒为 false")
    void viewerStateForAuthorSkipsLookup() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));

        assertFalse(followService.viewerFollowedAuthor(88L));

        verifyNoInteractions(followMapper);
    }

    // ---- 个人主页的三份自数据读路径 ----

    @Test
    @DisplayName("我关注的列表：属主取自登录态，不接收客户端传入的用户 ID")
    void listFollowingUsesSessionOwner() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(followMapper.findFollowing(88L))
                .thenReturn(List.of(UserBriefVO.builder().id(1L).username("wqq").build()));

        PageResult<UserBriefVO> result = followService.listFollowing(1, 10);

        verify(followMapper).findFollowing(88L);
        assertEquals(1L, result.getTotal().longValue());
        assertEquals(1, result.getList().get(0).getId().intValue());
    }

    @Test
    @DisplayName("粉丝列表：按被关注维度取，不与「我关注的」互换查询")
    void listFollowersUsesAuthorColumn() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(followMapper.findFollowers(88L))
                .thenReturn(List.of(UserBriefVO.builder().id(3L).username("alex").build()));

        PageResult<UserBriefVO> result = followService.listFollowers(1, 10);

        verify(followMapper).findFollowers(88L);
        verify(followMapper, never()).findFollowing(any());
        assertEquals("alex", result.getList().get(0).getUsername());
    }

    @Test
    @DisplayName("统计行：关注数取 follower 维度、粉丝数取 author 维度，不得互换")
    void profileStatsMapsEachColumnToTheRightNumber() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(followMapper.countByFollower(88L)).thenReturn(7);
        when(followMapper.countByAuthor(88L)).thenReturn(12);

        ProfileStatsVO stats = followService.profileStats();

        assertEquals(7, stats.getFollowingCount().intValue(), "following 是「我关注了多少人」");
        assertEquals(12, stats.getFollowersCount().intValue(), "followers 是「多少人关注了我」");
    }

    @Test
    @DisplayName("登录态查不到用户时拒绝列出自数据，且不落查询")
    void listWithoutResolvableUserThrows() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ghost@ayun.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(userMapper.findByEmail("ghost@ayun.com")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> followService.listFollowing(1, 10));

        verify(followMapper, never()).findFollowing(any());
    }

    // ---- 作者主页两栏的关注标注 ----

    @Test
    @DisplayName("作者主页「粉丝」栏：两个方向各批量查一次，双向命中即互相关注，自己的那一行不标注")
    void authorPageFollowerRowsCarryViewerState() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).build());
        when(followMapper.findFollowers(1L)).thenReturn(List.of(
                UserBriefVO.builder().id(3L).username("alex").build(),
                UserBriefVO.builder().id(9L).username("wqq").build(),
                UserBriefVO.builder().id(88L).username("reader").build()));
        when(followMapper.findFollowedAmong(88L, List.of(3L, 9L, 88L))).thenReturn(List.of(3L, 9L));
        when(followMapper.findFollowingMeAmong(88L, List.of(3L, 9L, 88L))).thenReturn(List.of(3L));

        List<UserBriefVO> rows = followService.listFollowersOf(1L, 1, 20).getList();

        assertEquals(Boolean.TRUE, rows.get(0).getFollowed(), "我关注了 alex");
        assertEquals(Boolean.TRUE, rows.get(0).getFollowsMe(), "alex 也关注了我，前端据此换成「互相关注」");
        assertEquals(Boolean.TRUE, rows.get(1).getFollowed());
        assertEquals(Boolean.FALSE, rows.get(1).getFollowsMe(), "未命中反向时是 false，不是 null");
        assertNull(rows.get(2).getFollowed(), "自己的那一行无从谈起关注");
        assertNull(rows.get(2).getFollowsMe());
    }

    @Test
    @DisplayName("匿名读作者主页两栏：保持 null 且不发起批量查询，让页面分得清「没登录」与「没关注」")
    void authorPageRowsStayUnannotatedForAnonymous() {
        when(userMapper.findById(1L)).thenReturn(User.builder().id(1L).build());
        when(followMapper.findFollowing(1L)).thenReturn(List.of(UserBriefVO.builder().id(3L).build()));

        UserBriefVO row = followService.listFollowingOf(1L, 1, 20).getList().get(0);

        assertNull(row.getFollowed());
        assertNull(row.getFollowsMe());
        verify(followMapper, never()).findFollowedAmong(any(), any());
        verify(followMapper, never()).findFollowingMeAmong(any(), any());
    }

    @Test
    @DisplayName("个人主页的「我关注的」不标注：那里的行本来就都关注着，行内只有取关")
    void personalPageListStaysUnannotated() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com")).thenReturn(reader(88L));
        when(followMapper.findFollowing(88L)).thenReturn(List.of(UserBriefVO.builder().id(3L).build()));

        UserBriefVO row = followService.listFollowing(1, 10).getList().get(0);

        assertNull(row.getFollowed());
        verify(followMapper, never()).findFollowedAmong(any(), any());
    }
}
