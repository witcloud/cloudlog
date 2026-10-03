package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.NotFoundException;
import com.ayun.entity.Like;
import com.ayun.entity.Post;
import com.ayun.entity.User;
import com.ayun.mapper.LikeMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.LikeStateVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞业务逻辑类。
 * 一人一赞由 likes 表的唯一键 uk_post_user 保证，应用层不重复校验；
 * like_count 每次写后按 likes 表实际条数绝对重算，不做 +1/-1 盲增（db-design.md §5.2）。
 */
@Service
public class LikeService {

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 点赞。重复点赞时 INSERT IGNORE 影响 0 行，静默成为幂等成功，不报错也不翻转状态。
     * @param postId 文章 ID
     * @return 服务端权威状态，前端按钮以此为准
     */
    @Transactional
    public LikeStateVO like(Long postId) {
        Long userId = currentUserId();
        requirePost(postId);
        likeMapper.insert(Like.builder().postId(postId).userId(userId).build());
        return applyAndReadState(postId, userId);
    }

    /**
     * 取消点赞。未点赞过时删除影响 0 行，同样返回幂等成功。
     * @param postId 文章 ID
     * @return 服务端权威状态，前端按钮以此为准
     */
    @Transactional
    public LikeStateVO unlike(Long postId) {
        Long userId = currentUserId();
        requirePost(postId);
        likeMapper.delete(postId, userId);
        return applyAndReadState(postId, userId);
    }

    /**
     * 当前浏览者是否已点赞该文章，用于文章详情的 liked 字段；匿名一律 false 且不查库
     * @param postId 文章 ID
     * @return 是否已点赞
     */
    public boolean viewerLikedPost(Long postId) {
        Long userId = currentUserIdOrNull();
        return userId != null && likeMapper.existsByPostAndUser(postId, userId);
    }

    private LikeStateVO applyAndReadState(Long postId, Long userId) {
        likeMapper.recalculateLikeCount(postId);
        return new LikeStateVO(likeMapper.existsByPostAndUser(postId, userId), likeMapper.countByPost(postId));
    }

    private void requirePost(Long postId) {
        Post post = postMapper.findById(postId);
        // 点赞只对已发布文章开放：未发布内容对普通用户按「不存在」响应，不泄露存在性
        if (post == null || !"published".equals(post.getStatus())) {
            throw new NotFoundException("文章不存在或已删除");
        }
    }

    private Long currentUserId() {
        User user = userMapper.findByEmail(SecurityContextHolder.getContext().getAuthentication().getName());
        if (user == null) {
            throw new BizException("登录状态无效，请重新登录");
        }
        return user.getId();
    }

    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        User user = userMapper.findByEmail(auth.getName());
        return user == null ? null : user.getId();
    }
}
