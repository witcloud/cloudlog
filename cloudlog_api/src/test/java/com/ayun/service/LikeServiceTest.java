package com.ayun.service;

import com.ayun.entity.Like;
import com.ayun.entity.Post;
import com.ayun.entity.User;
import com.ayun.common.NotFoundException;
import com.ayun.mapper.LikeMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.LikeStateVO;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 点赞服务单元测试：覆盖一人一赞的幂等语义与 like_count 绝对重算
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeMapper likeMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private LikeService likeService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsReader() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reader@ayun.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    @DisplayName("首次点赞：以登录态身份写入点赞记录并重算计数，返回已点赞的真实状态")
    void likeFirstTimeInsertsRecordAndRecalculatesCount() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com"))
                .thenReturn(User.builder().id(88L).email("reader@ayun.com").build());
        when(postMapper.findById(1L)).thenReturn(Post.builder().id(1L).status("published").build());
        when(likeMapper.insert(any(Like.class))).thenReturn(1);
        when(likeMapper.existsByPostAndUser(1L, 88L)).thenReturn(true);
        when(likeMapper.countByPost(1L)).thenReturn(7);

        LikeStateVO state = likeService.like(1L);

        ArgumentCaptor<Like> captor = ArgumentCaptor.forClass(Like.class);
        verify(likeMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getPostId().intValue(), "post_id 取自路径参数");
        assertEquals(88, captor.getValue().getUserId().intValue(), "user_id 应来自登录态");
        verify(likeMapper).recalculateLikeCount(1L);
        assertTrue(state.isLiked());
        assertEquals(7, state.getLikeCount().intValue());
    }

    @Test
    @DisplayName("重复点赞幂等：唯一键冲突时不报错、不取消已有点赞，仍重算并返回已点赞状态")
    void likeTwiceIsIdempotent() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com"))
                .thenReturn(User.builder().id(88L).email("reader@ayun.com").build());
        when(postMapper.findById(1L)).thenReturn(Post.builder().id(1L).status("published").build());
        when(likeMapper.insert(any(Like.class))).thenReturn(0);
        when(likeMapper.existsByPostAndUser(1L, 88L)).thenReturn(true);
        when(likeMapper.countByPost(1L)).thenReturn(7);

        LikeStateVO state = likeService.like(1L);

        verify(likeMapper, never()).delete(any(), any());
        verify(likeMapper).recalculateLikeCount(1L);
        assertTrue(state.isLiked(), "重复点赞不得翻转成未点赞");
        assertEquals(7, state.getLikeCount().intValue());
    }

    @Test
    @DisplayName("取消点赞：按 (post_id, user_id) 删除并重算计数，返回未点赞状态")
    void unlikeDeletesAndRecalculatesCount() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com"))
                .thenReturn(User.builder().id(88L).email("reader@ayun.com").build());
        when(postMapper.findById(1L)).thenReturn(Post.builder().id(1L).status("published").build());
        when(likeMapper.existsByPostAndUser(1L, 88L)).thenReturn(false);
        when(likeMapper.countByPost(1L)).thenReturn(6);

        LikeStateVO state = likeService.unlike(1L);

        verify(likeMapper).delete(1L, 88L);
        verify(likeMapper, never()).insert(any(Like.class));
        verify(likeMapper).recalculateLikeCount(1L);
        assertFalse(state.isLiked());
        assertEquals(6, state.getLikeCount().intValue());
    }

    @Test
    @DisplayName("取消后重新点赞：再次落库一条记录并重算，状态回到已点赞")
    void likeAfterUnlikeInsertsAgain() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com"))
                .thenReturn(User.builder().id(88L).email("reader@ayun.com").build());
        when(postMapper.findById(1L)).thenReturn(Post.builder().id(1L).status("published").build());
        when(likeMapper.existsByPostAndUser(1L, 88L)).thenReturn(false, true);
        when(likeMapper.countByPost(1L)).thenReturn(6, 7);

        likeService.unlike(1L);
        LikeStateVO state = likeService.like(1L);

        verify(likeMapper).delete(1L, 88L);
        verify(likeMapper).insert(any(Like.class));
        assertTrue(state.isLiked());
        assertEquals(7, state.getLikeCount().intValue());
    }

    @Test
    @DisplayName("点赞目标文章不存在：抛出未找到异常，不留孤儿点赞记录")
    void likeMissingPostThrowsNotFound() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com"))
                .thenReturn(User.builder().id(88L).email("reader@ayun.com").build());
        when(postMapper.findById(404L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> likeService.like(404L));

        verify(likeMapper, never()).insert(any(Like.class));
        verify(likeMapper, never()).recalculateLikeCount(any());
    }

    @Test
    @DisplayName("点赞未发布文章：按「不存在」拒绝，不泄露草稿存在性")
    void likeUnpublishedPostThrowsNotFound() {
        loginAsReader();
        when(userMapper.findByEmail("reader@ayun.com"))
                .thenReturn(User.builder().id(88L).email("reader@ayun.com").build());
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("draft").authorId(7L).build());

        assertThrows(NotFoundException.class, () -> likeService.like(9L));

        verify(likeMapper, never()).insert(any(Like.class));
        verify(likeMapper, never()).recalculateLikeCount(any());
    }

    @Test
    @DisplayName("未登录浏览文章详情：不查库，点赞状态恒为 false")
    void viewerStateForAnonymousSkipsLookup() {
        assertFalse(likeService.viewerLikedPost(1L));

        verifyNoInteractions(likeMapper, userMapper);
    }
}
