package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.NotFoundException;
import com.ayun.entity.BannedWord;
import com.ayun.entity.Comment;
import com.ayun.entity.Post;
import com.ayun.mapper.CommentMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论业务逻辑类
 */
@Service
public class CommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private BannedWordService bannedWordService;

    @Autowired
    private ArticleService articleService;

    /**
     * 根据文章 ID 获取所有评论及其回复（仅已通过审核的评论）。
     * 文章不存在或对当前请求不可见时按「不存在」响应，不泄露未发布文章的评论
     * @param postId 文章 ID
     * @return 评论列表（包含嵌套的回复）
     */
    public List<Comment> getCommentsByPostId(Long postId) {
        Post post = postMapper.findById(postId);
        if (post == null || !articleService.isViewable(post)) {
            throw new NotFoundException("文章不存在或无权访问");
        }
        List<Comment> rootComments = commentMapper.findByPostId(postId);
        if (rootComments.isEmpty()) {
            // 一篇文章零评论是常态，这条路径上一行都不该查
            return rootComments;
        }
        // 回复按父评论 ID 一次批量取回：逐条根评论单查，等于一屏根评论数就是那么多条 SQL
        List<Integer> rootIds = new ArrayList<>(rootComments.size());
        for (Comment comment : rootComments) {
            rootIds.add(comment.getId());
        }
        Map<Integer, List<Comment>> repliesByParentId = new HashMap<>();
        for (Comment reply : commentMapper.findRepliesByParentIds(rootIds)) {
            repliesByParentId.computeIfAbsent(reply.getParentId(), key -> new ArrayList<>()).add(reply);
        }
        for (Comment comment : rootComments) {
            comment.setReplies(repliesByParentId.getOrDefault(comment.getId(), List.of()));
        }
        return rootComments;
    }

    /**
     * 发表评论。评论人身份一律取自登录态，请求体里的 userId 直接覆盖，避免冒充他人发表；
     * 只允许对已发布文章评论，回复还要求父评论是同文章、已过审的根评论（见 requireReplyableParent）；
     * 先过评论防护（字数上限、同人发布间隔，0 均表示不限制），
     * 再按两层审核规则决定初始状态：comment_moderation 关闭 → 直接 approved；
     * 开启时先过违禁词库机审，命中 reject 词直接拒绝（不入库），命中 pending 词落 pending 并记下命中词，未命中直接 approved。
     * @param comment 评论对象
     * @return 该评论的最终审核状态（'approved' 或 'pending'），前端据此区分「已发布」与「待审核」
     */
    @Transactional
    public String addComment(Comment comment) {
        if (comment.getContent() == null || comment.getContent().isBlank()) {
            throw new BizException("评论内容不能为空");
        }

        // 单机部署，防护直接查库即可，不引入 Redis 之类的额外中间件
        int maxLength = settingsService.getInt(SettingsService.KEY_COMMENT_MAX_LENGTH,
                SettingsService.DEFAULT_COMMENT_MAX_LENGTH);
        if (maxLength > 0 && comment.getContent().length() > maxLength) {
            throw new BizException("评论最多 " + maxLength + " 字");
        }

        String email = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        com.ayun.entity.User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BizException("登录状态无效，请重新登录");
        }

        Post post = postMapper.findById(comment.getPostId());
        if (post == null || !"published".equals(post.getStatus())) {
            throw new NotFoundException("文章不存在或无权访问");
        }
        requireReplyableParent(comment);
        comment.setUserId(user.getId());

        int intervalSeconds = settingsService.getInt(SettingsService.KEY_COMMENT_INTERVAL_SECONDS,
                SettingsService.DEFAULT_COMMENT_INTERVAL_SECONDS);
        if (intervalSeconds > 0) {
            // 按该用户最近一条已落库的评论计时：回复也算，转待审的也算（被机审拒掉的没入库，不占间隔）
            LocalDateTime lastAt = commentMapper.findLastCreatedAtByUserId(user.getId());
            if (lastAt != null && Duration.between(lastAt, LocalDateTime.now()).getSeconds() < intervalSeconds) {
                throw new BizException("操作过于频繁，请稍后再试");
            }
        }

        boolean moderation = settingsService.getBoolean(SettingsService.KEY_COMMENT_MODERATION, true);
        // 关审核即整个跳过机审，reject 词也不拦：这个开关是机审与人工审核的总闸（business-model.md §2.6）
        BannedWordService.Match match = moderation ? bannedWordService.match(comment.getContent()) : null;
        if (match != null && BannedWord.ACTION_REJECT.equals(match.action())) {
            throw new BizException("评论包含违规内容，无法发表");
        }

        // status 与 hitWord 一律由服务端决定，请求体里带什么都被这两行覆盖
        comment.setStatus(match != null ? "pending" : "approved");
        comment.setHitWord(match != null ? match.word() : null);
        commentMapper.insert(comment);
        if (match == null) {
            postMapper.incrementCommentCount(comment.getPostId());
        }
        return comment.getStatus();
    }

    /**
     * 回复目标要当场核得过：这条评论存在、与本次是同一篇文章、已过审、且自身是根评论。
     * 这四条过去一条都不查，于是 parentId 可以指向别的文章的评论（前台按文章装载，那条回复永远显示不出来，
     * 只在库里留下一行跨文章的孤儿）、指向待审或被拒的评论（等于绕过人工审核把内容挂到别人评论下）、
     * 或指向一条回复（评论只有两级，读侧不装载第三级，写了也是不可见数据）
     */
    private void requireReplyableParent(Comment comment) {
        Integer parentId = comment.getParentId();
        if (parentId == null || parentId == 0) {
            return;
        }
        Comment parent = commentMapper.findById(parentId);
        if (parent == null
                || !parent.getPostId().equals(comment.getPostId())
                || !"approved".equals(parent.getStatus())
                || (parent.getParentId() != null && parent.getParentId() != 0)) {
            throw new BizException("回复的评论不存在或不可回复");
        }
    }
}
