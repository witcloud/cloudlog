package com.ayun.service;

import com.ayun.common.BizException;
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
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 关注业务逻辑类。
 * 一人对一作者只有一条关系，由 follows 表的唯一键 uk_author_follower 保证，应用层不重复校验；
 * 关注数每次写后按 follows 表实际条数绝对重算读取，不维护冗余列、不做 +1/-1 盲增。
 */
@Service
public class FollowService {

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 关注作者。重复关注时 INSERT IGNORE 影响 0 行，静默成为幂等成功，不报错也不翻转状态。
     * @param authorId 被关注用户 ID
     * @return 服务端权威状态，前端按钮以此为准
     */
    @Transactional
    public FollowStateVO follow(Long authorId) {
        Long followerId = currentUserId();
        requireUser(authorId);
        if (authorId.equals(followerId)) {
            throw new BizException("不能关注自己");
        }
        followMapper.insert(Follow.builder().authorId(authorId).followerId(followerId).build());
        return readState(authorId, followerId);
    }

    /**
     * 取消关注。未关注过时删除影响 0 行，同样返回幂等成功。
     * @param authorId 被关注用户 ID
     * @return 服务端权威状态，前端按钮以此为准
     */
    @Transactional
    public FollowStateVO unfollow(Long authorId) {
        Long followerId = currentUserId();
        requireUser(authorId);
        followMapper.delete(authorId, followerId);
        return readState(authorId, followerId);
    }

    /**
     * 当前浏览者是否已关注该作者，用于文章详情的 author.followed 字段；匿名一律 false 且不查库
     * @param authorId 被关注用户 ID
     * @return 是否已关注
     */
    public boolean viewerFollowedAuthor(Long authorId) {
        Long followerId = currentUserIdOrNull();
        if (followerId == null || followerId.equals(authorId)) {
            return false;
        }
        return followMapper.existsByAuthorAndFollower(authorId, followerId);
    }

    /**
     * 统计某作者的被关注数，用于文章详情的 author.followers 字段
     * @param authorId 被关注用户 ID
     * @return 被关注数
     */
    public int countFollowers(Long authorId) {
        return followMapper.countByAuthor(authorId);
    }

    /**
     * 统计某用户关注了多少人，用于作者主页的统计行
     * @param userId 用户 ID
     * @return 其关注的人数
     */
    public int countFollowing(Long userId) {
        return followMapper.countByFollower(userId);
    }

    /**
     * 作者主页用的三态关注判定：匿名浏览者无从「已关注」谈起，返回 null 而不是 false，
     * 让页面能区分「没登录」和「登录了但没关注」。文章详情仍走 viewerFollowedAuthor 的既有口径
     * @param authorId 被关注用户 ID
     * @return 已登录时为是否关注，未登录时为 null
     */
    public Boolean viewerFollowedAuthorOrNull(Long authorId) {
        return currentUserIdOrNull() == null ? null : viewerFollowedAuthor(authorId);
    }

    /**
     * 个人主页统计行：我关注的与关注我的两个计数
     * @return 计数视图
     */
    public ProfileStatsVO profileStats() {
        Long userId = currentUserId();
        return new ProfileStatsVO(followMapper.countByFollower(userId), followMapper.countByAuthor(userId));
    }

    /**
     * 我关注的用户列表。属主只取自登录态，不接受客户端传入的用户 ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页的用户简要视图
     */
    public PageResult<UserBriefVO> listFollowing(Integer page, Integer pageSize) {
        Long followerId = currentUserId();
        startPage(page, pageSize);
        return toPage(followMapper.findFollowing(followerId));
    }

    /**
     * 关注我的用户列表。属主只取自登录态
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页的用户简要视图
     */
    public PageResult<UserBriefVO> listFollowers(Integer page, Integer pageSize) {
        Long authorId = currentUserId();
        startPage(page, pageSize);
        return toPage(followMapper.findFollowers(authorId));
    }

    /**
     * 作者主页「关注中」栏：任意用户关注的名单，公开可读，行内带浏览者自己的关注状态。
     * 与 listFollowing 的区别只在属主来源——这条按客户端传入的 ID 查，所以先确认这个人公开可见
     * @param userId 作者用户 ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页的用户简要视图（同样不含邮箱与角色）
     */
    public PageResult<UserBriefVO> listFollowingOf(Long userId, Integer page, Integer pageSize) {
        requireVisibleUser(userId);
        startPage(page, pageSize);
        return toPage(annotateViewerState(followMapper.findFollowing(userId)));
    }

    /**
     * 作者主页「粉丝」栏：关注某人的名单，公开可读，行内带浏览者自己的关注状态（回关用）
     * @param userId 作者用户 ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页的用户简要视图（同样不含邮箱与角色）
     */
    public PageResult<UserBriefVO> listFollowersOf(Long userId, Integer page, Integer pageSize) {
        requireVisibleUser(userId);
        startPage(page, pageSize);
        return toPage(annotateViewerState(followMapper.findFollowers(userId)));
    }

    /**
     * 标注这一页行与浏览者的双向关系，供行内按钮决定「关注 / 取消关注 / 互相关注」。
     * 匿名与个人页列表都不标注，保持 null——前端据此区分「没登录」和「登录了但没关注」。
     * 必须在分页查询之后调用：PageHelper 的 ThreadLocal 只作用于紧随其后的那一次查询
     * @param rows 当页的行（原地改写并原样返回，PageInfo 的计数仍取自同一个 Page）
     * @return 标注后的同一批行
     */
    private List<UserBriefVO> annotateViewerState(List<UserBriefVO> rows) {
        Long viewerId = currentUserIdOrNull();
        if (viewerId == null || rows.isEmpty()) {
            return rows;
        }
        List<Long> ids = rows.stream().map(UserBriefVO::getId).toList();
        Set<Long> iFollow = new HashSet<>(followMapper.findFollowedAmong(viewerId, ids));
        Set<Long> followMe = new HashSet<>(followMapper.findFollowingMeAmong(viewerId, ids));
        for (UserBriefVO row : rows) {
            // 自己的那一行无从谈起关注，两个方向都留 null
            boolean self = viewerId.equals(row.getId());
            row.setFollowed(self ? null : iFollow.contains(row.getId()));
            row.setFollowsMe(self ? null : followMe.contains(row.getId()));
        }
        return rows;
    }

    private void startPage(Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
    }

    private PageResult<UserBriefVO> toPage(List<UserBriefVO> list) {
        PageInfo<UserBriefVO> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize(), list);
    }

    private FollowStateVO readState(Long authorId, Long followerId) {
        return new FollowStateVO(
                followMapper.existsByAuthorAndFollower(authorId, followerId),
                followMapper.countByAuthor(authorId));
    }

    private void requireUser(Long authorId) {
        if (userMapper.findById(authorId) == null) {
            throw new NotFoundException("用户不存在或已注销");
        }
    }

    /**
     * 作者主页读路径的取人判据：不存在、已注销、已禁用三种情况同一个 404、同一句话。
     * 只约束这一页的主语（TA 是谁），名单里的成员行不过滤——关注数是 COUNT(follows) 的结果，
     * 把禁用中的成员从名单里挑掉就会让「行数」与「计数」对不上；留痕那条口径见 business-model.md §6
     */
    private void requireVisibleUser(Long userId) {
        if (userMapper.findVisibleById(userId) == null) {
            throw new NotFoundException("用户不存在或已注销");
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
