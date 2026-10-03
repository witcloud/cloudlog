package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.NotFoundException;
import com.ayun.entity.BannedWord;
import com.ayun.entity.Comment;
import com.ayun.entity.Post;
import com.ayun.entity.User;
import com.ayun.mapper.CommentMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论服务单元测试：覆盖评论人身份收口、回复目标的四条核验（存在/同文章/已过审/自身是根评论）、
 * 评论防护（字数上限与同人间隔）、两层审核规则（开关 + 词库机审的两级动作）、计数时机，
 * 以及列表回复的批量装载。
 * 词库自身的匹配与缓存规则在 {@link BannedWordServiceTest}，这里只桩住 match 的结果。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SettingsService settingsService;

    @Mock
    private BannedWordService bannedWordService;

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private CommentService commentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 桩成服务的真实缺省值。不桩的话 mock 对 getInt 一律回 0，两条守卫在全部既有用例里都会静默失效，
     * 「测过了」就变成了假绿
     */
    @BeforeEach
    void guardDefaults() {
        lenient().when(settingsService.getInt(SettingsService.KEY_COMMENT_MAX_LENGTH,
                SettingsService.DEFAULT_COMMENT_MAX_LENGTH)).thenReturn(500);
        lenient().when(settingsService.getInt(SettingsService.KEY_COMMENT_INTERVAL_SECONDS,
                SettingsService.DEFAULT_COMMENT_INTERVAL_SECONDS)).thenReturn(60);
    }

    private void loginAsReader() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reader@ayun.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(userMapper.findByEmail("reader@ayun.com"))
                .thenReturn(User.builder().id(88L).email("reader@ayun.com").build());
    }

    /** addComment 的前置门禁：目标必须是已发布文章 */
    private void publishedPostExists() {
        when(postMapper.findById(1L))
                .thenReturn(Post.builder().id(1L).status("published").build());
    }

    private void moderationEnabled() {
        when(settingsService.getBoolean(SettingsService.KEY_COMMENT_MODERATION, true)).thenReturn(true);
    }

    /** 机审命中一个指定动作的词 */
    private void machineAuditHits(String action, String word) {
        when(bannedWordService.match(anyString()))
                .thenReturn(new BannedWordService.Match(action, word));
    }

    private Comment inserted() {
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        return captor.getValue();
    }

    /** 桩出一条回复目标：归属哪篇文章、什么审核状态、自身是不是根评论，三条都由调用点决定 */
    private void parentComment(Integer id, Long postId, String status, Integer parentOfParent) {
        when(commentMapper.findById(id)).thenReturn(Comment.builder()
                .id(id).postId(postId).status(status).parentId(parentOfParent).build());
    }

    @Test
    @DisplayName("发表评论：请求体里的 userId 被登录用户覆盖，无法冒充他人发表")
    void addCommentIgnoresClientSuppliedUserId() {
        loginAsReader();
        publishedPostExists();
        moderationEnabled();
        Comment payload = Comment.builder().postId(1L).content("冒充他人").userId(7L).build();

        String status = commentService.addComment(payload);

        assertEquals(88, inserted().getUserId().intValue(), "user_id 应来自登录态而非请求体");
        assertEquals("approved", status, "机审未命中应直接通过");
    }

    @Test
    @DisplayName("发表评论：命中 pending 词时落待审、记下命中词且不累加评论数")
    void addCommentWithPendingWordStaysPendingAndRecordsHitWord() {
        loginAsReader();
        publishedPostExists();
        moderationEnabled();
        machineAuditHits(BannedWord.ACTION_PENDING, "广告");

        commentService.addComment(Comment.builder().postId(1L).content("最棒的广告平台").build());

        Comment saved = inserted();
        assertEquals("pending", saved.getStatus(), "命中 pending 词应转待审");
        assertEquals("广告", saved.getHitWord(), "待审评论要记下首个命中词，审核台才解释得清");
        verify(postMapper, never()).incrementCommentCount(anyLong());
    }

    @Test
    @DisplayName("发表评论：命中 reject 词时直接拒绝，评论不入库也不计数")
    void addCommentWithRejectWordIsRefused() {
        loginAsReader();
        publishedPostExists();
        moderationEnabled();
        machineAuditHits(BannedWord.ACTION_REJECT, "代开发票");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> commentService.addComment(Comment.builder().postId(1L).content("代开发票请联系").build()));

        assertEquals("评论包含违规内容，无法发表", error.getMessage(), "拒绝原因要能直接展示给用户");
        verify(commentMapper, never()).insert(any(Comment.class));
        verify(postMapper, never()).incrementCommentCount(anyLong());
    }

    @Test
    @DisplayName("发表评论：开启审核但未命中违禁词时直接通过并累加评论数")
    void addCommentWithModerationWithoutHitIsApproved() {
        loginAsReader();
        publishedPostExists();
        moderationEnabled();

        String status = commentService.addComment(Comment.builder().postId(1L).content("写得真好").build());

        Comment saved = inserted();
        assertEquals("approved", saved.getStatus());
        assertNull(saved.getHitWord(), "未命中不该留下命中词");
        assertEquals("approved", status);
        verify(postMapper).incrementCommentCount(1L);
    }

    @Test
    @DisplayName("发表评论：未开审核时整个跳过机审，reject 词也不拦")
    void addCommentWithoutModerationSkipsMachineAudit() {
        loginAsReader();
        publishedPostExists();
        when(settingsService.getBoolean(SettingsService.KEY_COMMENT_MODERATION, true)).thenReturn(false);

        String status = commentService.addComment(Comment.builder().postId(1L).content("内容").build());

        assertEquals("approved", inserted().getStatus());
        assertEquals("approved", status);
        verify(bannedWordService, never()).match(anyString());
        verify(postMapper).incrementCommentCount(1L);
    }

    @Test
    @DisplayName("发表评论：状态由服务端决定，请求体伪造 approved 无效")
    void addCommentIgnoresClientSuppliedStatus() {
        loginAsReader();
        publishedPostExists();
        moderationEnabled();
        machineAuditHits(BannedWord.ACTION_PENDING, "广告");

        commentService.addComment(Comment.builder().postId(1L).content("广告位招租").status("approved").build());

        assertEquals("pending", inserted().getStatus());
    }

    @Test
    @DisplayName("发表评论：命中词由服务端决定，请求体伪造 hitWord 无效")
    void addCommentIgnoresClientSuppliedHitWord() {
        loginAsReader();
        publishedPostExists();
        moderationEnabled();

        commentService.addComment(Comment.builder().postId(1L).content("写得真好").hitWord("伪造词").build());

        assertNull(inserted().getHitWord(), "hit_word 只能由机审写，客户端塞的值要被覆盖掉");
    }

    @Test
    @DisplayName("发表评论：目标文章不存在或未发布时按「不存在」拒绝，不落库")
    void addCommentToMissingOrUnpublishedPostRejected() {
        loginAsReader();
        when(postMapper.findById(9L))
                .thenReturn(Post.builder().id(9L).status("draft").authorId(7L).build());

        assertThrows(NotFoundException.class,
                () -> commentService.addComment(Comment.builder().postId(9L).content("内容").build()));
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("回复校验：父评论属于另一篇文章时拒绝，不留跨文章的孤儿回复")
    void replyToCommentOnAnotherPostIsRefused() {
        loginAsReader();
        publishedPostExists();
        parentComment(7, 2L, "approved", 0);

        BizException error = assertThrows(BizException.class, () -> commentService.addComment(
                Comment.builder().postId(1L).content("跨文章回复").parentId(7).build()));

        assertEquals("回复的评论不存在或不可回复", error.getMessage());
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("回复校验：父评论还在待审时拒绝，否则等于绕过人工审核把内容挂到它下面")
    void replyToPendingCommentIsRefused() {
        loginAsReader();
        publishedPostExists();
        parentComment(7, 1L, "pending", 0);

        BizException error = assertThrows(BizException.class, () -> commentService.addComment(
                Comment.builder().postId(1L).content("回复待审评论").parentId(7).build()));

        assertEquals("回复的评论不存在或不可回复", error.getMessage());
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("回复校验：给一条回复再回复被拒，评论只有两级，三级写了也读不出来")
    void replyToAReplyIsRefused() {
        loginAsReader();
        publishedPostExists();
        parentComment(7, 1L, "approved", 3);

        BizException error = assertThrows(BizException.class, () -> commentService.addComment(
                Comment.builder().postId(1L).content("回复的回复").parentId(7).build()));

        assertEquals("回复的评论不存在或不可回复", error.getMessage());
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("评论防护：超过字数上限时拒绝，报错里带上上限值，且不入库不计数")
    void addCommentExceedingMaxLengthIsRefused() {
        RuntimeException error = assertThrows(RuntimeException.class, () -> commentService.addComment(
                Comment.builder().postId(1L).content("字".repeat(501)).build()));

        assertEquals("评论最多 500 字", error.getMessage(), "提示要写清上限，用户才知道该删多少");
        verify(commentMapper, never()).insert(any(Comment.class));
        verify(postMapper, never()).incrementCommentCount(anyLong());
    }

    @Test
    @DisplayName("评论防护：距上一条评论不足间隔秒数时拒绝，报错不泄露具体还需等多久")
    void addCommentWithinIntervalIsRefused() {
        loginAsReader();
        publishedPostExists();
        when(commentMapper.findLastCreatedAtByUserId(88L)).thenReturn(LocalDateTime.now().minusSeconds(30));

        RuntimeException error = assertThrows(RuntimeException.class, () -> commentService.addComment(
                Comment.builder().postId(1L).content("再刷一条").build()));

        assertEquals("操作过于频繁，请稍后再试", error.getMessage());
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("评论防护：间隔按用户算，回复与待审评论同样占住间隔；已过间隔则放行")
    void addCommentIntervalCountsEveryCommentByTheUser() {
        loginAsReader();
        publishedPostExists();
        moderationEnabled();
        // 这条回复的目标本身是合法的，让用例真正测到的是间隔而不是父评论校验
        parentComment(7, 1L, "approved", 0);
        // 最近一条是三十秒前落库的待审评论：它没展示出来，但一样占住间隔
        when(commentMapper.findLastCreatedAtByUserId(88L)).thenReturn(LocalDateTime.now().minusSeconds(30));
        assertThrows(RuntimeException.class,
                () -> commentService.addComment(Comment.builder().postId(1L).content("回复").parentId(7).build()));

        when(commentMapper.findLastCreatedAtByUserId(88L)).thenReturn(LocalDateTime.now().minusSeconds(61));
        assertEquals("approved",
                commentService.addComment(Comment.builder().postId(1L).content("过了间隔再发").build()));
    }

    @Test
    @DisplayName("评论防护：两项都配成 0 时即不限制，超长且刚发过也照样落库")
    void zeroLimitsDisableBothGuards() {
        when(settingsService.getInt(SettingsService.KEY_COMMENT_MAX_LENGTH,
                SettingsService.DEFAULT_COMMENT_MAX_LENGTH)).thenReturn(0);
        when(settingsService.getInt(SettingsService.KEY_COMMENT_INTERVAL_SECONDS,
                SettingsService.DEFAULT_COMMENT_INTERVAL_SECONDS)).thenReturn(0);
        loginAsReader();
        publishedPostExists();

        String status = commentService.addComment(
                Comment.builder().postId(1L).content("字".repeat(600)).build());

        assertEquals("approved", status);
        verify(commentMapper).insert(any(Comment.class));
        // 0 即不限制：连计时用的查询都不该发出去
        verify(commentMapper, never()).findLastCreatedAtByUserId(anyLong());
    }

    // ---- 评论列表的回复装载：一次批量查询，不随根评论数放大 ----

    /** 文章可见性由 ArticleService 判，本类只桩住它的结论 */
    private void viewablePostWithRoots(List<Comment> roots) {
        when(postMapper.findById(1L)).thenReturn(Post.builder().id(1L).status("published").build());
        when(articleService.isViewable(any())).thenReturn(true);
        when(commentMapper.findByPostId(1L)).thenReturn(roots);
    }

    @Test
    @DisplayName("评论列表：回复按父评论 ID 一次批量取回，不再逐条根评论各发一条查询")
    void repliesAreLoadedWithOneBatchQuery() {
        viewablePostWithRoots(List.of(
                Comment.builder().id(11).postId(1L).parentId(0).build(),
                Comment.builder().id(12).postId(1L).parentId(0).build()));
        when(commentMapper.findRepliesByParentIds(List.of(11, 12))).thenReturn(List.of(
                Comment.builder().id(21).postId(1L).parentId(11).build(),
                Comment.builder().id(22).postId(1L).parentId(12).build()));

        List<Comment> roots = commentService.getCommentsByPostId(1L);

        assertEquals(21, roots.get(0).getReplies().get(0).getId(), "回复要回填到各自那条根评论下");
        assertEquals(22, roots.get(1).getReplies().get(0).getId());
        verify(commentMapper).findRepliesByParentIds(List.of(11, 12));
    }

    @Test
    @DisplayName("评论列表：没有根评论时直接返回，一条回复查询都不发")
    void emptyRootListSendsNoReplyQuery() {
        viewablePostWithRoots(List.of());

        assertTrue(commentService.getCommentsByPostId(1L).isEmpty());
        verify(commentMapper, never()).findRepliesByParentIds(any());
    }
}
