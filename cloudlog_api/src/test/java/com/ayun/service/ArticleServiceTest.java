package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.common.IpUtils;
import com.ayun.common.NotFoundException;
import com.ayun.entity.Post;
import com.ayun.entity.Tag;
import com.ayun.entity.User;
import com.ayun.mapper.CommentMapper;
import com.ayun.mapper.LikeMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.TagMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.converter.PostConverter;
import com.ayun.model.dto.PostCreateDTO;
import com.ayun.model.dto.PostQueryDTO;
import com.ayun.model.dto.PostUpdateDTO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostIdVO;
import com.ayun.model.vo.PostVO;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文章服务单元测试：覆盖发布链路（创建返回 ID、发布时间补写）、未发布文章的访问控制与阅读计数口径
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private LikeMapper likeMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SettingsService settingsService;

    /** 详情读路径上 visit_logs 那一条链路的协作者；本类不断言它的行为，只需要它存在且为空操作 */
    @Mock
    private VisitLogService visitLogService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    /** 转换器为无依赖的纯映射，用真实实现，避免在测试里复刻它的逻辑 */
    @Spy
    private PostConverter postConverter = new PostConverter();

    /** 来源地址同样用真实实现（采信代理头那一档）：访问者标识与访问日志里的地址都由它算 */
    @Spy
    private IpUtils ipUtils = new IpUtils(true);

    @InjectMocks
    private ArticleService articleService;

    private static final String DEDUP_KEY = "article:view_dedup:9";

    /** 阅读去重窗口（秒），与 ArticleService 内联常量同值 */
    private static final long VIEW_WINDOW = 3600L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        // PageHelper.startPage 在 mock 的 Mapper 上不会被消费，不清会漏到下一个用例
        PageHelper.clearPage();
    }

    private void loginAs(Long userId, String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(userMapper.findByEmail(email)).thenReturn(User.builder().id(userId).email(email).build());
    }

    private void loginAsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    }

    /** 匿名访问者的来源地址靠请求对象取得，读路径用例必须先绑定一个（X-Forwarded-For 为空时用 remoteAddr 127.0.0.1） */
    private void bindRequest(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * 打桩去重脚本：firstVisit 对应脚本返回的新成员数。
     * 桩本身就按访问者标识、键与窗口秒数匹配，标识算错或窗口没带上都会让桩落空（返回 null → 不计阅读）
     * @param member 期望的访问者标识（user:{id} 或 ip:{ip}）
     * @param firstVisit 本窗口内是否首次访问
     */
    private void stubDedup(String member, boolean firstVisit) {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(DEDUP_KEY)), eq(member), eq(VIEW_WINDOW))).thenReturn(firstVisit ? 1L : 0L);
    }

    /** 断言去重脚本被按同样的参数调过一次 */
    private void verifyDedupScript(String member) {
        verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(DEDUP_KEY)), eq(member), eq(VIEW_WINDOW));
    }

    // ---- R1：创建文章必须把新 ID 交回前端，否则编辑器无法切换到更新路径 ----

    @Test
    @DisplayName("创建文章：id 由应用层雪花生成，并回填作者 ID")
    void createPostReturnsNewId() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").content("正文").status("draft").build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        PostIdVO result = articleService.createPost(new PostCreateDTO());

        assertNotNull(result.getId(), "文章 ID 须由 Service 层显式生成，不再依赖数据库自增回填");
        assertEquals(result.getId(), post.getId(), "返回的 ID 即落库那篇的 ID");
        assertEquals(7, post.getAuthorId().intValue());

        PostIdVO next = articleService.createPost(new PostCreateDTO());
        assertTrue(next.getId() > result.getId(), "连续两篇的雪花 ID 不得重复且随时间递增");
        assertTrue(result.getId() >= 1_000_000_000_000_000_000L, "雪花 ID 为 19 位");
    }

    @Test
    @DisplayName("创建文章：立即发布时写入发布时间")
    void createPostSetsPublishedAtWhenPublishing() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").content("正文").status("published").categoryId(1).build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        articleService.createPost(new PostCreateDTO());

        assertNotNull(post.getPublishedAt());
    }

    // ---- R5：转为已发布但从未记过发布时间时补写，且不得覆盖既有时间 ----

    @Test
    @DisplayName("更新文章：草稿转已发布时补写发布时间")
    void updatePostBackfillsPublishedAt() {
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("draft").categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("published");

        articleService.updatePost(3L, updateDTO);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).update(captor.capture());
        assertNotNull(captor.getValue().getPublishedAt(), "首次转已发布应补写 published_at");
    }

    @Test
    @DisplayName("更新文章：已发布文章的既有发布时间不被覆盖")
    void updatePostKeepsExistingPublishedAt() {
        LocalDateTime original = LocalDateTime.of(2026, 9, 1, 10, 0);
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("published")
                .publishedAt(original).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setTitle("新标题");

        articleService.updatePost(3L, updateDTO);

        assertEquals(original, stored.getPublishedAt());
    }

    // ---- R10：摘要是正文的服务端派生物，作者侧没有输入口（business-model.md §2.3）----

    @Test
    @DisplayName("创建文章：立即发布时从正文派生摘要")
    void createPostGeneratesExcerptWhenPublishing() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").status("published").categoryId(1)
                .content("## 第一段\n\n这是文章开头的**文字**，用来派生摘要。").build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        articleService.createPost(new PostCreateDTO());

        assertEquals("第一段 这是文章开头的文字，用来派生摘要。", post.getExcerpt());
    }

    @Test
    @DisplayName("创建文章：定时发布同样派生，调度器翻转时摘要已在库里")
    void createPostGeneratesExcerptWhenScheduling() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").status("scheduled").categoryId(1)
                .content("正文开头一句话").build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));
        PostCreateDTO createDTO = new PostCreateDTO();
        createDTO.setCategoryId(1);
        createDTO.setPublishTime(LocalDateTime.now().plusDays(1));

        articleService.createPost(createDTO);

        assertEquals("正文开头一句话", post.getExcerpt(),
                "定时发布在落库时就该有摘要：调度器那条批量 UPDATE 不加载实体，钩子挂不到它身上");
    }

    @Test
    @DisplayName("创建文章：草稿永不派生摘要")
    void createDraftKeepsExcerptEmpty() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").status("draft").content("正文").build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        articleService.createPost(new PostCreateDTO());

        assertNull(post.getExcerpt(), "草稿正文还在改，此刻的开头几句话不代表这篇文章");
    }

    @Test
    @DisplayName("更新文章：下线后重新上线补齐缺失的摘要")
    void republishFillsMissingExcerpt() {
        Post stored = Post.builder().id(3L).title("标题").status("offline").categoryId(1)
                .publishedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .content("重新上线时该有摘要").build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("published");

        articleService.updatePost(3L, updateDTO);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).update(captor.capture());
        assertEquals("重新上线时该有摘要", captor.getValue().getExcerpt());
    }

    @Test
    @DisplayName("更新文章：已发布文章再改正文不重算摘要，列表与 description 才不随保存漂移")
    void contentEditOnPublishedKeepsExcerpt() {
        Post stored = Post.builder().id(3L).title("标题").status("published").categoryId(1)
                .publishedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .excerpt("旧正文派生的那一句")
                .content("旧正文").build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setContent("新正文的开头完全不同了");

        articleService.updatePost(3L, updateDTO);

        assertEquals("旧正文派生的那一句", stored.getExcerpt());
    }

    @Test
    @DisplayName("更新文章：转入下线不改写摘要")
    void offlineLeavesExcerptAlone() {
        loginAs(7L, "admin@ayun.com");
        Post stored = Post.builder().id(3L).title("标题").status("published").categoryId(1)
                .publishedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .content("正文").build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("offline");

        articleService.updatePost(3L, updateDTO);

        assertNull(stored.getExcerpt(), "下线只是收回可见性，不该顺带生成对外文案");
    }

    // ---- R6：scheduled 的计划时刻必须落进 published_at，否则调度器的 published_at <= NOW() 永不命中 ----

    @Test
    @DisplayName("创建文章：定时发布时把计划时刻写入发布时间")
    void createPostStoresScheduledPublishTime() {
        loginAs(7L, "admin@ayun.com");
        LocalDateTime planned = LocalDateTime.now().plusDays(1);
        PostCreateDTO createDTO = new PostCreateDTO();
        createDTO.setCategoryId(1);
        createDTO.setPublishTime(planned);
        Post post = Post.builder().title("标题").content("正文").status("scheduled").categoryId(1).build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        articleService.createPost(createDTO);

        assertEquals(planned, post.getPublishedAt());
    }

    @Test
    @DisplayName("创建文章：定时发布缺计划时刻时拒绝落库")
    void createPostRejectsScheduledWithoutPublishTime() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").content("正文").status("scheduled").build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        assertThrows(RuntimeException.class, () -> articleService.createPost(new PostCreateDTO()));
        verify(postMapper, never()).insert(any(Post.class));
    }

    @Test
    @DisplayName("创建/更新文章：status 只认四个合法值，非法值拒在入口而不是去撞 ENUM")
    void illegalStatusIsRefusedBeforeWrite() {
        PostCreateDTO createDTO = new PostCreateDTO();
        createDTO.setStatus("archived");
        BizException createError = assertThrows(BizException.class, () -> articleService.createPost(createDTO));
        assertEquals("非法的文章状态", createError.getMessage());
        verify(postMapper, never()).insert(any(Post.class));

        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("publish");
        BizException updateError = assertThrows(BizException.class,
                () -> articleService.updatePost(3L, updateDTO));
        assertEquals("非法的文章状态", updateError.getMessage());
        // 判在查库之前：连这一次 SELECT 都不该发出去
        verify(postMapper, never()).findById(3L);
    }

    @Test
    @DisplayName("更新文章：offline 在白名单内（后台下线走的就是这条 PUT），不带 status 也照常放行")
    void offlineAndAbsentStatusPassTheWhitelist() {
        loginAs(7L, "admin@ayun.com");
        Post stored = Post.builder().id(3L).status("published").categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO offline = new PostUpdateDTO();
        offline.setStatus("offline");

        articleService.updatePost(3L, offline);

        assertEquals("offline", stored.getStatus());

        PostUpdateDTO contentOnly = new PostUpdateDTO();
        contentOnly.setTitle("改个标题");
        articleService.updatePost(3L, contentOnly);
        assertEquals("改个标题", stored.getTitle(), "不带 status 即本次不改状态，不该被白名单误拒");
    }

    @Test
    @DisplayName("更新文章：转为定时发布时写入计划时刻")
    void updatePostStoresScheduledPublishTime() {
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("draft").categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        LocalDateTime planned = LocalDateTime.now().plusDays(2);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("scheduled");
        updateDTO.setPublishTime(planned);

        articleService.updatePost(3L, updateDTO);

        assertEquals(planned, stored.getPublishedAt());
    }

    @Test
    @DisplayName("更新文章：定时发布的计划时刻已过时拒绝落库")
    void updatePostRejectsScheduledWithPastPublishTime() {
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("draft").build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("scheduled");
        updateDTO.setPublishTime(LocalDateTime.now().minusHours(1));

        assertThrows(RuntimeException.class, () -> articleService.updatePost(3L, updateDTO));
        verify(postMapper, never()).update(any(Post.class));
    }

    @Test
    @DisplayName("更新文章：不带 status 的纯内容保存不动状态与计划时刻")
    void updatePostWithoutStatusKeepsScheduleIntact() {
        LocalDateTime planned = LocalDateTime.now().plusDays(1);
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("scheduled")
                .publishedAt(planned).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setTitle("改过的标题");

        articleService.updatePost(3L, updateDTO);

        assertEquals("scheduled", stored.getStatus(), "自动保存不得把定时文章打回其他状态");
        assertEquals(planned, stored.getPublishedAt(), "自动保存缺 publishTime 时不得改写计划时刻");
    }

    @Test
    @DisplayName("我赞过的文章：属主取自登录态，只走 findLikedByUser")
    void likedPostsUseSessionOwner() {
        loginAs(7L, "reader@ayun.com");
        when(postMapper.findLikedByUser(7L)).thenReturn(List.of(
                Post.builder().id(9L).title("标题").status("published").build()));
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());

        PageResult<PostVO> result = articleService.getLikedPosts(1, 10);

        verify(postMapper).findLikedByUser(7L);
        assertEquals(1, result.getList().size());
        assertEquals(9, result.getList().get(0).getId().intValue());
    }

    // ---- R4：未发布文章只对作者与管理员可见 ----

    @Test
    @DisplayName("详情：匿名访问草稿按不存在处理，且不计阅读量")
    void anonymousCannotReadDraft() {
        loginAsAnonymous();
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("draft").authorId(7L).build());

        assertNull(articleService.getArticleById(9L));
        verify(postMapper, never()).incrementViewCount(9L);
    }

    @Test
    @DisplayName("详情：匿名访问定时发布文章按不存在处理")
    void anonymousCannotReadScheduled() {
        loginAsAnonymous();
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("scheduled").authorId(7L).build());

        assertNull(articleService.getArticleById(9L));
    }

    @Test
    @DisplayName("详情：他人（非管理员）访问草稿同样不可见")
    void otherUserCannotReadDraft() {
        loginAs(88L, "reader@ayun.com");
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("draft").authorId(7L).build());

        assertNull(articleService.getArticleById(9L));
    }

    @Test
    @DisplayName("详情：作者可以读取自己的草稿，编辑器回显不受影响，且不产生任何阅读计数")
    void authorCanReadOwnDraftWithoutCounting() {
        loginAs(7L, "author@ayun.com");
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).status("draft").authorId(7L).viewCount(12).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());

        PostVO result = articleService.getArticleById(9L);

        assertNotNull(result);
        assertEquals(9, result.getId().intValue());
        assertEquals(12, result.getViews().intValue(), "预览草稿不得改写阅读数");
        verify(postMapper, never()).incrementViewCount(9L);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("详情：管理员可读他人未发布文章，定时发布预览同样不计阅读量")
    void adminCanReadOthersDraft() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@ayun.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(userMapper.findByEmail("admin@ayun.com"))
                .thenReturn(User.builder().id(1L).email("admin@ayun.com").role("admin").build());
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("scheduled").authorId(7L).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());

        assertNotNull(articleService.getArticleById(9L));
        verify(postMapper, never()).incrementViewCount(9L);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("详情：已发布文章无需登录即可读取，匿名按 IP 计入阅读")
    void publishedArticleIsPublic() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("published").authorId(7L).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        stubDedup("ip:127.0.0.1", true);

        assertNotNull(articleService.getArticleById(9L));
    }

    @Test
    @DisplayName("详情：文章不存在时返回 null")
    void missingArticleReturnsNull() {
        when(postMapper.findById(404L)).thenReturn(null);

        assertNull(articleService.getArticleById(404L));
    }

    // ---- R7：删除文章是全站唯一实现，必须先删子行再删主行（business-model.md §5.7 第 7 条）----

    @Test
    @DisplayName("删除文章：按 post_tags → likes → comments → posts 的顺序清理")
    void deletePostClearsChildRowsBeforeMainRow() {
        when(postMapper.findById(1L)).thenReturn(Post.builder().id(1L).status("published").build());

        articleService.deletePost(1L);

        InOrder order = inOrder(tagMapper, likeMapper, commentMapper, postMapper);
        order.verify(tagMapper).deletePostTags(1L);
        order.verify(likeMapper).deleteByPostId(1L);
        order.verify(commentMapper).deleteByPostId(1L);
        order.verify(postMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除文章：文章不存在时不产生任何删除")
    void deletePostWithoutArticleDeletesNothing() {
        when(postMapper.findById(404L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> articleService.deletePost(404L));
        verify(postMapper, never()).deleteById(404L);
        verify(tagMapper, never()).deletePostTags(any());
        verify(likeMapper, never()).deleteByPostId(any());
        verify(commentMapper, never()).deleteByPostId(any());
    }

    // ---- 草稿箱删除与清空共用同一条级联实现，不得只删主表 ----

    @Test
    @DisplayName("删除草稿：属主谓词确认后走同一条级联，先删子行再删主行")
    void deleteDraftCascadesThroughSharedImplementation() {
        loginAs(7L, "author@ayun.com");
        when(postMapper.findOwnedDraft(9L, 7L))
                .thenReturn(Post.builder().id(9L).status("scheduled").authorId(7L).build());

        articleService.deleteDraft(9L);

        InOrder order = inOrder(tagMapper, likeMapper, commentMapper, postMapper);
        order.verify(tagMapper).deletePostTags(9L);
        order.verify(likeMapper).deleteByPostId(9L);
        order.verify(commentMapper).deleteByPostId(9L);
        order.verify(postMapper).deleteById(9L);
    }

    @Test
    @DisplayName("删除草稿：非本人或不存在（属主谓词未命中）时按不存在拒绝，不产生任何删除")
    void deleteDraftOfOthersDeletesNothing() {
        loginAs(88L, "reader@ayun.com");
        when(postMapper.findOwnedDraft(9L, 88L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> articleService.deleteDraft(9L));
        verify(postMapper, never()).deleteById(9L);
        verify(tagMapper, never()).deletePostTags(any());
        verify(likeMapper, never()).deleteByPostId(any());
        verify(commentMapper, never()).deleteByPostId(any());
    }

    @Test
    @DisplayName("清空草稿箱：逐篇走同一条级联实现")
    void clearDraftsCascadesEachDraft() {
        loginAs(7L, "author@ayun.com");
        when(postMapper.findDraftIdsByAuthor(7L)).thenReturn(List.of(9L, 10L));

        articleService.clearDrafts();

        verify(postMapper).findDraftIdsByAuthor(7L);
        verify(postMapper).deleteById(9L);
        verify(postMapper).deleteById(10L);
        verify(likeMapper).deleteByPostId(9L);
        verify(likeMapper).deleteByPostId(10L);
        verify(commentMapper).deleteByPostId(9L);
        verify(commentMapper).deleteByPostId(10L);
    }

    // ---- 状态机：scheduled 撤下为 draft 必须清空计划时刻 ----

    @Test
    @DisplayName("更新文章：定时文章撤下为草稿时清空 published_at")
    void updatePostScheduledToDraftClearsPublishedAt() {
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("scheduled")
                .publishedAt(LocalDateTime.now().plusDays(1)).categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("draft");

        articleService.updatePost(3L, updateDTO);

        verify(postMapper).clearPublishedAt(3L);
    }

    @Test
    @DisplayName("更新文章：published → draft 不清空既有发布时间（首次发布时刻不回退）")
    void updatePostPublishedToDraftKeepsPublishedAt() {
        LocalDateTime original = LocalDateTime.of(2026, 9, 1, 10, 0);
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("published")
                .publishedAt(original).categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("draft");

        articleService.updatePost(3L, updateDTO);

        verify(postMapper, never()).clearPublishedAt(3L);
        assertEquals(original, stored.getPublishedAt());
    }

    // ---- 状态机：offline 属「未发布可见域」，published_at 在下线与重新上线之间不被改写 ----

    @Test
    @DisplayName("更新文章：published → offline 保留首发时刻，并写下架留痕，不走清空计划时刻的旁路")
    void updatePostPublishedToOfflineKeepsPublishedAt() {
        loginAs(7L, "author@ayun.com");
        LocalDateTime original = LocalDateTime.of(2026, 9, 1, 10, 0);
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("published")
                .publishedAt(original).categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("offline");

        articleService.updatePost(3L, updateDTO);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).update(captor.capture());
        assertEquals("offline", captor.getValue().getStatus());
        assertEquals(original, captor.getValue().getPublishedAt(),
                "下线要留住首发时刻，它是重新上线后详情页「发布于」的值");
        assertEquals(7L, captor.getValue().getOfflinedBy(), "留痕记下是谁撤下来的");
        assertNotNull(captor.getValue().getOfflinedAt(), "留痕记下是什么时候撤下来的");
        verify(postMapper, never()).clearPublishedAt(3L);
    }

    @Test
    @DisplayName("更新文章：offline → published 不清空下架留痕（放回去的人抹不掉它）")
    void updatePostOfflineToPublishedKeepsOfflineTrace() {
        LocalDateTime tracedAt = LocalDateTime.of(2026, 9, 2, 8, 0);
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("offline")
                .publishedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .offlinedBy(9L).offlinedAt(tracedAt).categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("published");

        articleService.updatePost(3L, updateDTO);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).update(captor.capture());
        assertEquals(9L, captor.getValue().getOfflinedBy(), "重新上线不得改写操作者留痕");
        assertEquals(tracedAt, captor.getValue().getOfflinedAt(), "重新上线不得清空时刻留痕");
    }

    @Test
    @DisplayName("更新文章：纯内容保存不写下架留痕")
    void updatePostWithoutOfflineTransitionWritesNoTrace() {
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("published")
                .publishedAt(LocalDateTime.of(2026, 9, 1, 10, 0)).categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setTitle("改过的标题");

        articleService.updatePost(3L, updateDTO);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).update(captor.capture());
        assertNull(captor.getValue().getOfflinedBy(), "没下线就不该有操作者");
        assertNull(captor.getValue().getOfflinedAt(), "没下线就不该有时刻");
    }

    @Test
    @DisplayName("更新文章：offline → published 不覆盖既有首发时刻")
    void updatePostOfflineToPublishedKeepsOriginalPublishedAt() {
        LocalDateTime original = LocalDateTime.of(2026, 9, 1, 10, 0);
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("offline")
                .publishedAt(original).categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("published");

        articleService.updatePost(3L, updateDTO);

        assertEquals("published", stored.getStatus());
        assertEquals(original, stored.getPublishedAt(),
                "重新上线沿用首次对外可见的时刻，不得被补写成当前时间");
        verify(postMapper, never()).clearPublishedAt(3L);
    }

    @Test
    @DisplayName("更新文章：转 offline 不受分类强制约束（offline 不是对外发布）")
    void updatePostToOfflineDoesNotRequireCategory() {
        loginAs(7L, "author@ayun.com");
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("published")
                .publishedAt(LocalDateTime.of(2026, 9, 1, 10, 0)).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("offline");

        articleService.updatePost(3L, updateDTO);

        verify(postMapper).update(stored);
    }

    @Test
    @DisplayName("更新文章：不带 status 的自动保存不把已下线文章打回草稿")
    void updatePostWithoutStatusKeepsOfflineIntact() {
        LocalDateTime original = LocalDateTime.of(2026, 9, 1, 10, 0);
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("offline")
                .publishedAt(original).categoryId(1).build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setTitle("改过的标题");

        articleService.updatePost(3L, updateDTO);

        assertEquals("offline", stored.getStatus(), "30 秒自动保存不得改写已下线文章的状态");
        assertEquals(original, stored.getPublishedAt());
    }

    // ---- offline 的可见性与计数：读者一律按不存在处理，作者本人可读但不计阅读 ----

    @Test
    @DisplayName("详情：匿名访问已下线文章按不存在处理，且不计阅读量")
    void anonymousCannotReadOffline() {
        loginAsAnonymous();
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("offline").authorId(7L).build());

        assertNull(articleService.getArticleById(9L));
        verify(postMapper, never()).incrementViewCount(9L);
    }

    @Test
    @DisplayName("详情：他人（非管理员）访问已下线文章同样不可见")
    void otherUserCannotReadOffline() {
        loginAs(88L, "reader@ayun.com");
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("offline").authorId(7L).build());

        assertNull(articleService.getArticleById(9L));
    }

    @Test
    @DisplayName("详情：作者可读自己的已下线文章，阅读计数停止增长")
    void authorCanReadOwnOfflineWithoutCounting() {
        loginAs(7L, "author@ayun.com");
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).status("offline").authorId(7L).viewCount(12).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());

        PostVO result = articleService.getArticleById(9L);

        assertNotNull(result);
        assertEquals("offline", result.getStatus());
        assertEquals(12, result.getViews().intValue(), "下线后阅读量不再增长");
        verify(postMapper, never()).incrementViewCount(9L);
        verifyNoInteractions(redisTemplate);
    }

    // ---- 「我的文章」：本人视角同时带出已发布与已下线，status 供前端打徽标 ----

    @Test
    @DisplayName("我的文章：属主取自登录态，已下线文章随 status 一起回传")
    void myPostsCarryOfflineStatusForBadge() {
        loginAs(7L, "author@ayun.com");
        when(postMapper.findMine(7L, null, null, null)).thenReturn(List.of(
                Post.builder().id(9L).title("在线").status("published").build(),
                Post.builder().id(10L).title("下线了").status("offline").build()));
        when(tagMapper.findByPostId(anyLong())).thenReturn(List.of());

        PageResult<PostVO> result = articleService.getMyPosts(1, 10, null, null, null);

        verify(postMapper).findMine(7L, null, null, null);
        assertEquals(2, result.getList().size());
        assertEquals("offline", result.getList().get(1).getStatus());
    }

    // ---- 分类强制：发布 / 定时必须已选分类，草稿免分类 ----

    @Test
    @DisplayName("创建文章：草稿免分类，可不含 categoryId 落库")
    void createPostAllowsDraftWithoutCategory() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").content("正文").status("draft").build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        articleService.createPost(new PostCreateDTO());

        verify(postMapper).insert(any(Post.class));
    }

    @Test
    @DisplayName("创建文章：立即发布缺分类时拒绝落库")
    void createPostRejectsPublishWithoutCategory() {
        loginAs(7L, "admin@ayun.com");
        Post post = Post.builder().title("标题").content("正文").status("published").build();
        doReturn(post).when(postConverter).toEntity(any(PostCreateDTO.class));

        assertThrows(RuntimeException.class, () -> articleService.createPost(new PostCreateDTO()));
        verify(postMapper, never()).insert(any(Post.class));
    }

    @Test
    @DisplayName("更新文章：转为已发布缺分类时拒绝落库")
    void updatePostRejectsPublishWithoutCategory() {
        Post stored = Post.builder().id(3L).title("标题").content("正文").status("draft").build();
        when(postMapper.findById(3L)).thenReturn(stored);
        PostUpdateDTO updateDTO = new PostUpdateDTO();
        updateDTO.setStatus("published");

        assertThrows(RuntimeException.class, () -> articleService.updatePost(3L, updateDTO));
        verify(postMapper, never()).update(any(Post.class));
    }

    // ---- R8：详情响应的两套计数口径（ui-design.md §3.2 作者卡）----

    @Test
    @DisplayName("详情：作者三格为全部已发布文章的聚合，与本篇计数不同源")
    void detailCarriesAuthorAggregatesAlongsideArticleCounters() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).status("published").authorId(7L).viewCount(41).likeCount(3).commentCount(2)
                .author(User.builder().id(7L).username("wqq").build())
                .build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        stubDedup("ip:127.0.0.1", true);
        when(postMapper.findAuthorStats(7L)).thenReturn(PostVO.AuthorVO.builder()
                .articleCount(6).likeCount(51).viewCount(874).build());

        PostVO result = articleService.getArticleById(9L);

        assertNotNull(result);
        assertEquals(3, result.getLikes().intValue());
        assertEquals(6, result.getAuthor().getArticleCount().intValue());
        assertEquals(51, result.getAuthor().getLikeCount().intValue());
        assertEquals(874, result.getAuthor().getViewCount().intValue());
    }

    @Test
    @DisplayName("详情：阅读量返回本次自增后的值，不再永远落后一次")
    void detailReturnsViewCountAfterThisRequestIncrement() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).status("published").authorId(7L).viewCount(41).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        stubDedup("ip:127.0.0.1", true);

        PostVO result = articleService.getArticleById(9L);

        assertEquals(42, result.getViews().intValue());
        verify(postMapper).incrementViewCount(9L);
    }

    // ---- R9：阅读计数只认真实阅读——已发布文章 + 访问者小时窗口去重 ----

    @Test
    @DisplayName("详情：本窗口首次访问才自增库侧阅读量，入集合与设 1 小时过期由同一条脚本带走")
    void firstVisitCountsTheStoredCounterAndOpensWindow() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).status("published").authorId(7L).viewCount(41).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        // 桩按「键 + 访问者标识 + 窗口秒数」匹配，脚本少了窗口参数就落不到桩，下面的 42 也就拿不到
        stubDedup("ip:127.0.0.1", true);

        PostVO result = articleService.getArticleById(9L);

        assertEquals(42, result.getViews().intValue());
        verify(postMapper).incrementViewCount(9L);
    }

    @Test
    @DisplayName("详情：同一访问者一小时窗口内再次访问不加阅读量，响应返回查库原值")
    void repeatVisitWithinHourCountsNothing() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).status("published").authorId(7L).viewCount(41).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        stubDedup("ip:127.0.0.1", false);

        PostVO result = articleService.getArticleById(9L);

        assertEquals(41, result.getViews().intValue(), "去重命中时不得凭空多一次阅读");
        verify(postMapper, never()).incrementViewCount(9L);
        // 去重照样要问过 Redis（否则返回不了 0），只是本次不写库、也不再新增成员
        verifyDedupScript("ip:127.0.0.1");
    }

    @Test
    @DisplayName("详情：登录访问者按 user:{id} 去重，不受出口 IP 影响")
    void loggedInVisitorDedupsByIdentity() {
        loginAs(88L, "reader@ayun.com");
        bindRequest("203.0.113.9");
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("published").authorId(7L).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        stubDedup("user:88", true);

        assertNotNull(articleService.getArticleById(9L));
    }

    @Test
    @DisplayName("详情：匿名访问者以 X-Forwarded-For 首值作标识")
    void anonymousVisitorUsesFirstForwardedForValue() {
        SecurityContextHolder.clearContext();
        bindRequest("203.0.113.7, 10.0.0.1");
        when(postMapper.findById(9L)).thenReturn(Post.builder().id(9L).status("published").authorId(7L).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        stubDedup("ip:203.0.113.7", true);

        assertNotNull(articleService.getArticleById(9L));
    }

    @Test
    @DisplayName("详情：view_count 为空按 0 起算——首次得 1，窗口内重复访问返回 0")
    void nullViewCountStartsFromZero() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(
                Post.builder().id(9L).status("published").authorId(7L).build(),
                Post.builder().id(9L).status("published").authorId(7L).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(DEDUP_KEY)), eq("ip:127.0.0.1"), eq(VIEW_WINDOW))).thenReturn(1L, 0L);

        assertEquals(1, articleService.getArticleById(9L).getViews().intValue());
        assertEquals(0, articleService.getArticleById(9L).getViews().intValue());
        verify(postMapper).incrementViewCount(9L);
    }

    // ---- 列表排序：首页「推荐」与侧栏热榜走 findAllBrief 的两个不同分支，各自锁死不被对方带跑 ----

    @Test
    @DisplayName("列表：sort=recommended 原样透传给 findAllBrief（首页「推荐」tab）")
    void recommendedSortReachesMapperVerbatim() {
        PostQueryDTO query = new PostQueryDTO();
        query.setSort("recommended");
        when(postMapper.findAllBrief(null, null, null, null, "recommended")).thenReturn(List.of());

        articleService.getArticles(query);

        verify(postMapper).findAllBrief(null, null, null, null, "recommended");
    }

    @Test
    @DisplayName("列表：请求不带 sort 时按 DTO 默认值 latest 查询——搜索页与作者主页因此不受新 tab 影响")
    void absentSortDefaultsToLatest() {
        when(postMapper.findAllBrief(null, null, null, null, "latest")).thenReturn(List.of());

        articleService.getArticles(new PostQueryDTO());

        verify(postMapper).findAllBrief(null, null, null, null, "latest");
    }

    @Test
    @DisplayName("列表：Service 不改写 sort 取值——归一由 findAllBrief 的 <otherwise> 分支承担，白名单只有一处实现")
    void unknownSortIsForwardedWithoutRewriting() {
        PostQueryDTO query = new PostQueryDTO();
        query.setSort("whatever");
        when(postMapper.findAllBrief(null, null, null, null, "whatever")).thenReturn(List.of());

        articleService.getArticles(query);

        verify(postMapper).findAllBrief(null, null, null, null, "whatever");
    }

    @Test
    @DisplayName("热榜：仍按纯热度序的 popular 查询，不因首页新增「推荐」而变成置顶优先")
    void hotArticlesKeepPureHeatOrder() {
        when(postMapper.findAllBrief(null, null, null, null, "popular")).thenReturn(List.of());

        articleService.getHotArticles(10);

        verify(postMapper).findAllBrief(null, null, null, null, "popular");
    }

    // ---- 详情页三个 SEO 值的合成（business-model.md §2.10；2026-10-03 起库里没有可填的 SEO 表）----

    @Test
    @DisplayName("详情：标题拼站点名、描述取摘要、关键词取标签名")
    void detailComposesSeoFieldsFromPostFields() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).title("文章标题").excerpt("一段摘要").status("published").authorId(7L).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of(
                Tag.builder().id(1).name("Java").build(),
                Tag.builder().id(2).name("教程").build()));
        when(settingsService.getSiteName()).thenReturn("CloudLog");
        stubDedup("ip:127.0.0.1", true);

        PostVO vo = articleService.getArticleById(9L);

        assertEquals("文章标题 - CloudLog", vo.getSeoTitle(), "站点名取 site_settings，与前台标题后缀同源");
        assertEquals("一段摘要", vo.getSeoDescription());
        assertEquals("Java, 教程", vo.getSeoKeywords());
    }

    @Test
    @DisplayName("详情：摘要为空、文章无标签时两个字段就留空，不编占位文案")
    void detailLeavesAbsentSeoFieldsEmpty() {
        SecurityContextHolder.clearContext();
        bindRequest(null);
        when(postMapper.findById(9L)).thenReturn(Post.builder()
                .id(9L).title("文章标题").status("published").authorId(7L).build());
        when(tagMapper.findByPostId(9L)).thenReturn(List.of());
        when(settingsService.getSiteName()).thenReturn("CloudLog");
        stubDedup("ip:127.0.0.1", true);

        PostVO vo = articleService.getArticleById(9L);

        assertNull(vo.getSeoDescription(), "摘要为空就留空，不给详情页现编一句描述");
        assertNull(vo.getSeoKeywords(), "无标签即为空，不塞占位文案");
    }
}
