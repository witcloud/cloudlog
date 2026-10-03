package com.ayun.service;

import cn.hutool.core.util.IdUtil;
import com.ayun.common.BizException;
import com.ayun.common.ExcerptUtils;
import com.ayun.common.IpUtils;
import com.ayun.common.NotFoundException;
import com.ayun.common.UserAgentClassifier;
import com.ayun.entity.Post;
import com.ayun.entity.Tag;
import com.ayun.mapper.CommentMapper;
import com.ayun.mapper.LikeMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.TagMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.converter.PostConverter;
import com.ayun.model.dto.PostCreateDTO;
import com.ayun.model.dto.PostQueryDTO;
import com.ayun.model.dto.PostUpdateDTO;
import com.ayun.model.vo.DraftPageVO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostIdVO;
import com.ayun.model.vo.PostVO;
import com.ayun.model.vo.StatusCountVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文章业务逻辑类
 */
@Service
public class ArticleService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostConverter postConverter;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private VisitLogService visitLogService;

    @Autowired
    private IpUtils ipUtils;

    private static final String VIEW_DEDUP_KEY = "article:view_dedup:";

    /** 阅读去重窗口（秒）：同一访问者在窗口内反复访问同一文章只计一次 */
    private static final long VIEW_DEDUP_WINDOW_SECONDS = 3600L;

    /** 热榜缺省条数，与控制器 @RequestParam 的 defaultValue 同值 */
    private static final int HOT_ARTICLES_DEFAULT_LIMIT = 10;

    /**
     * 热榜单次最多返回条数。这条是公开端点、首页侧栏每次渲染都调用，不给上限就等于让一个 ?limit=100000
     * 把整张已发布表装进响应
     */
    private static final int HOT_ARTICLES_MAX_LIMIT = 30;

    /** posts.status 是这四值的 ENUM（db-design.md §2.2），白名单与它同源；越界值撞 MySQL 严格模式是 500 */
    private static final List<String> ALLOWED_STATUS = List.of("draft", "published", "scheduled", "offline");

    /**
     * 业务时区：计划时刻、发布时间的判定统一按北京墙钟。jackson 的 time-zone 与 JDBC 的
     * serverTimezone 都锚在 GMT+8（application.yml），JVM 时区不同也不会错位
     */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 去重的写入与过期合成一条脚本发给 Redis。分三条命令发就有崩溃缝：SADD 之后、EXPIRE 之前进程挂掉，
     * 集合留下一个没有 TTL 的键，此后这篇文章的窗口永不关闭，阅读计数静默停摆且不报错。
     * 语义与原三步一致：SADD 后若 TTL 仍为负（键刚建立，或改造前遗留的无过期时间旧键）才设过期，返回本次新增的成员数。
     * 包内可见只为让集成测试直接对真 Redis 跑一次脚本；生产路径只经 isFirstVisitWithinHour 进来
     */
    static final RedisScript<Long> VIEW_DEDUP_SCRIPT = RedisScript.of(
            "local added = redis.call('SADD', KEYS[1], ARGV[1])\n"
                    + "if tonumber(redis.call('TTL', KEYS[1])) < 0 then\n"
                    + "    redis.call('EXPIRE', KEYS[1], ARGV[2])\n"
                    + "end\n"
                    + "return added", Long.class);

    /**
     * 分页查询当前用户的未发布文章，并带回分状态计数供页头的状态视图使用
     */
    public DraftPageVO getDrafts(Integer pageNum, Integer pageSize, Integer year, Integer month,
                                 String keyword, String status) {
        com.ayun.entity.User user = currentUser();
        PageHelper.startPage(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        List<Post> list = postMapper.findDrafts(user.getId(), keyword, year, month, unpublishedStatus(status));
        PageInfo<Post> pageInfo = new PageInfo<>(list);
        // 计数查询排在分页查询之后：PageHelper 的 ThreadLocal 只被紧随的第一条查询消费，换序会被一起分页
        StatusCountVO counts = postMapper.countUnpublishedByStatus(user.getId(), keyword, year, month);

        DraftPageVO result = new DraftPageVO();
        result.setTotal(pageInfo.getTotal());
        result.setPage(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setList(postConverter.toVOList(fillTags(list)));
        result.setDraftCount(counts.getDraftCount());
        result.setScheduledCount(counts.getScheduledCount());
        return result;
    }

    /**
     * 只认未发布的两种状态，其余一律按「不筛」处理，免得这个端点被拿去列已发布
     */
    private static String unpublishedStatus(String status) {
        return "draft".equals(status) || "scheduled".equals(status) ? status : null;
    }

    /**
     * 分页查询当前用户「我的文章」：已发布 + 已下线。
     * 已下线的文章读者访问详情一律 404，但作者本人要能在自己主页上看到并认出它（PostVO 带 status 供前端打徽标）
     */
    public PageResult<PostVO> getMyPosts(Integer page, Integer pageSize,
                                         Integer year, Integer month, String keyword) {
        com.ayun.entity.User user = currentUser();
        PageHelper.startPage(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        List<Post> list = postMapper.findMine(user.getId(), keyword, year, month);
        return toPageResult(list, new PageInfo<>(list));
    }

    /**
     * 分页查询当前用户点赞过的已发布文章，按点赞时刻倒序
     */
    public PageResult<PostVO> getLikedPosts(Integer page, Integer pageSize) {
        com.ayun.entity.User user = currentUser();
        PageHelper.startPage(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        List<Post> list = postMapper.findLikedByUser(user.getId());
        return toPageResult(list, new PageInfo<>(list));
    }

    /**
     * 作者主页「点赞」栏：任意用户点赞过的已发布文章，公开只读。
     * 与 getLikedPosts 的差别只在属主来源，因此共用同一条 findLikedByUser 与同一份装配
     * @param userId 作者用户 ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页的文章视图
     */
    public PageResult<PostVO> getLikedPostsOf(Long userId, Integer page, Integer pageSize) {
        if (userMapper.findById(userId) == null) {
            throw new NotFoundException("用户不存在或已注销");
        }
        PageHelper.startPage(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        List<Post> list = postMapper.findLikedByUser(userId);
        return toPageResult(list, new PageInfo<>(list));
    }

    /**
     * 逐条补齐标签后组装分页结果
     */
    private PageResult<PostVO> toPageResult(List<Post> list, PageInfo<Post> pageInfo) {
        return PageResult.of(
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                postConverter.toVOList(fillTags(list))
        );
    }

    /**
     * 列表 VO 需要标签，但标签在子表里：按本页文章 ID 一次批量取回再分组装配。
     * 逐条 findByPostId 在页容量 50 时就是 51 条 SQL
     */
    private List<Post> fillTags(List<Post> list) {
        if (list.isEmpty()) {
            return list;
        }
        List<Long> postIds = new ArrayList<>(list.size());
        for (Post post : list) {
            postIds.add(post.getId());
        }
        Map<Long, List<Tag>> tagsByPostId = new HashMap<>();
        for (Map<String, Object> row : tagMapper.findByPostIds(postIds)) {
            Tag tag = new Tag();
            tag.setId(((Number) row.get("id")).intValue());
            tag.setName((String) row.get("name"));
            long postId = ((Number) row.get("post_id")).longValue();
            tagsByPostId.computeIfAbsent(postId, key -> new ArrayList<>()).add(tag);
        }
        for (Post post : list) {
            post.setTags(tagsByPostId.getOrDefault(post.getId(), List.of()));
        }
        return list;
    }

    /**
     * 删除单篇未发布文章（草稿与定时发布）。属主三条件先经 SQL 谓词确认，再走全站唯一的级联删除。
     * 事务必须挂在这里而不是 cascadeDelete：private 方法上的 @Transactional 不生效，
     * 不挂的话四条 DELETE 各自 auto-commit，中途失败会留下「主行还在、子行已删」的脏状态
     */
    @Transactional
    public void deleteDraft(Long id) {
        com.ayun.entity.User user = currentUser();
        Post post = postMapper.findOwnedDraft(id, user.getId());
        if (post == null) {
            throw new NotFoundException("草稿不存在或无权删除");
        }
        cascadeDelete(id);
    }

    /**
     * 清空当前用户的草稿箱（草稿与定时发布）：逐条走同一条级联实现，不留子行孤儿。
     * 整箱同事务：清到一半失败全部回滚，不会留下删了一半的草稿箱
     */
    @Transactional
    public void clearDrafts() {
        com.ayun.entity.User user = currentUser();
        for (Long id : postMapper.findDraftIdsByAuthor(user.getId())) {
            cascadeDelete(id);
        }
    }

    /**
     * 全站唯一的删除级联实现（business-model.md §5.7 第 7 条）：
     * 同一事务内按 post_tags → likes → comments → posts 的顺序先删子行、最后删主行。
     * deletePost、deleteDraft、clearDrafts 都复用它，不得另写只删主表的旁路
     */
    private void cascadeDelete(Long id) {
        tagMapper.deletePostTags(id);
        likeMapper.deleteByPostId(id);
        commentMapper.deleteByPostId(id);
        postMapper.deleteById(id);
    }

    /**
     * 详情页对外的三个 SEO 值由这一条合成，不查库（business-model.md §2.10）：
     * 标题 = 文章标题 + 站点名后缀（站点名取 `site_settings.site_name`，与前台标题后缀同源）、
     * 描述 = `posts.excerpt`、关键词 = 标签名按「, 」拼接，无标签即为空、不写占位文案。
     * 没有可填的 SEO 表之后，同一篇文章任何时候都得到同一份合成值；
     * 描述那一路的稳定性来自摘要何时冻结（§4.2 第 6 条），不来自这里
     */
    private void fillResolvedSeo(PostVO vo, Post post) {
        vo.setSeoTitle(post.getTitle() + " - " + settingsService.getSiteName());
        vo.setSeoDescription(post.getExcerpt());
        vo.setSeoKeywords(fallbackKeywords(post.getTags()));
    }

    /**
     * 关键词回退：标签名按逗号拼接；无标签即空，不给占位文案
     */
    private String fallbackKeywords(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (Tag tag : tags) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(tag.getName());
        }
        return joined.toString();
    }

    /**
     * 定时发布的计划时刻：调度器按 published_at 到期命中，缺它文章永不自动发布。
     * 文案与 DTO 的 @Future 消息一致，避免同一规则出现两套说法。
     * 「当前时间」固定取 Asia/Shanghai：publishTime 经 @JsonFormat(GMT+8) 反序列化就是北京墙钟，
     * 若用 JVM 默认时区的 now() 对比，容器以 UTC 运行时校验会错位 8 小时
     */
    private LocalDateTime requireFuturePublishTime(LocalDateTime publishTime) {
        if (publishTime == null) {
            throw new BizException("请选择定时发布时间");
        }
        if (publishTime.isBefore(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new BizException("定时发布时间需晚于当前时间");
        }
        return publishTime;
    }

    private com.ayun.entity.User currentUser() {
        String email = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        com.ayun.entity.User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BizException("当前用户不存在");
        }
        return user;
    }

    /**
     * 分页查询文章列表
     * @param queryDTO 查询参数
     * @return 分页结果
     */
    public PageResult<PostVO> getArticles(PostQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<Post> list = postMapper.findAllBrief(
                queryDTO.getAuthorId(),
                queryDTO.getCategoryId(),
                queryDTO.getTagId(),
                queryDTO.getKeyword(),
                queryDTO.getSort()
        );
        PageInfo<Post> pageInfo = new PageInfo<>(list);

        // 为列表中的文章填充标签（与本类其他列表共用批量装配）
        List<PostVO> voList = postConverter.toVOList(fillTags(list));

        return PageResult.of(
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                voList
        );
    }

    /**
     * 获取侧栏热榜文章（按加权热度分倒序，不是按阅读量倒序）
     * 热度分 =（阅读 + 点赞×3 + 评论×5）÷ 发布天数的 0.6 次幂，表达式见 PostMapper.xml 的 heatScore 片段
     * @param limit 返回条数
     * @return 文章 VO 列表
     */
    public List<PostVO> getHotArticles(Integer limit) {
        // 越界按上限钳而不拒：这是首页的一次取数，报 400 只会让侧栏空掉，钳住同样达到限流目的
        int size = limit == null || limit <= 0 ? HOT_ARTICLES_DEFAULT_LIMIT : Math.min(limit, HOT_ARTICLES_MAX_LIMIT);
        PageHelper.startPage(1, size);
        List<Post> list = postMapper.findAllBrief(null, null, null, null, "popular");
        return postConverter.toVOList(list);
    }

    /**
     * 根据 ID 获取文章详情
     * 未发布文章（草稿、定时发布、已下线）只对作者本人与管理员可见，其他人一律按「不存在」处理，避免泄露存在性
     * @param id 文章 ID
     * @return 文章 VO，不可见时返回 null
     */
    public PostVO getArticleById(Long id) {
        Post post = postMapper.findById(id);
        if (post == null || !isViewable(post)) {
            return null;
        }
        post.setTags(tagMapper.findByPostId(id));
        // 阅读量只认真实阅读：未发布内容（作者预览草稿/定时、已下线文章）不计数，已发布的按访问者小时窗口去重。
        // bot 判定必须参与 counted：visit_logs 写入侧会丢弃爬虫，这里不一起判的话，
        // 爬虫访问会推高 view_count 却不留日志行，两侧对不上账
        String userAgent = clientUserAgent();
        boolean counted = "published".equals(post.getStatus())
                && !UserAgentClassifier.isBot(userAgent)
                && isFirstVisitWithinHour(id);
        if (counted) {
            // 阅读量落在库侧：DB 列是展示与排序的真源，Redis 只承担去重，不再维护第二个计数器
            postMapper.incrementViewCount(id);
            // 与 +1 同一判据、同一时刻：一次窗口首访 = view_count +1 = visit_logs 一行，两侧天然可对账。
            // 原始地址与 UA 必须在这条（请求）线程上取——recordVisit 是 @Async，换线程后取不到请求上下文
            visitLogService.recordVisit(id, ipUtils.clientIp(), userAgent);
        }
        // 响应体与是否真的自增保持一致：本次没 +1 就原样返回查库值，重复访问不会凭空多一次阅读。
        // view_count 建表可空，取不到值时按 0 起算，不让读路径抛空指针
        post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + (counted ? 1 : 0));
        PostVO vo = postConverter.toVO(post);
        // SEO 三元组只在详情装配：是响应现算的回落值，列表带它就得每行多算一次（business-model.md §2.10）
        fillResolvedSeo(vo, post);
        // 作者卡是「全部已发布文章」口径，与本篇计数分属两条查询；排在自增之后，使两者对本次访问的计数一致
        PostVO.AuthorVO author = vo.getAuthor();
        if (author != null) {
            PostVO.AuthorVO stats = postMapper.findAuthorStats(post.getAuthorId());
            author.setArticleCount(stats.getArticleCount());
            author.setLikeCount(stats.getLikeCount());
            author.setViewCount(stats.getViewCount());
        }
        return vo;
    }

    /**
     * 未发布内容（草稿、定时发布、已下线）的可见性门禁：匿名拒绝，作者本人或管理员放行。
     * offline 与 draft/scheduled 同属「未发布可见域」，走的是同一条判定，读者访问详情一律按不存在处理（404）。
     * 文章详情与文章评论列表共用同一条判定，不产生任何计数副作用
     */
    public boolean isViewable(Post post) {
        return "published".equals(post.getStatus()) || isOwnerOrAdmin(post);
    }

    /**
     * 属主或博主判定，不带状态放行：未发布内容（草稿、定时、已下线）只对这两类人可见，
     * 其余请求者一律按「不存在」处理，不泄露文章是否存在（business-model.md §4.3 第 2 条）
     */
    private boolean isOwnerOrAdmin(Post post) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return false;
        }
        com.ayun.entity.User user = userMapper.findByEmail(auth.getName());
        if (user == null) {
            return false;
        }
        return "admin".equals(user.getRole()) || user.getId().equals(post.getAuthorId());
    }

    /**
     * 小时窗口去重：访问者标识写进 article:view_dedup:{postId} 集合，脚本返回的新成员数 > 0 即本窗口内的首次访问。
     * 过期只在键刚建立（尚无 TTL）时设置——若每个新访客都续期，持续流量会让窗口永不关闭，先来的访客反而永久不再计数
     */
    private boolean isFirstVisitWithinHour(Long id) {
        Long added = redisTemplate.execute(VIEW_DEDUP_SCRIPT, List.of(VIEW_DEDUP_KEY + id),
                visitorKey(), VIEW_DEDUP_WINDOW_SECONDS);
        return added != null && added > 0L;
    }

    /**
     * 访问者标识：已登录按 user:{id}（与点赞/关注同源，从安全上下文回库取），匿名或查不到用户时按 ip:{ip}
     */
    private String visitorKey() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "ip:" + ipUtils.clientIp();
        }
        com.ayun.entity.User user = userMapper.findByEmail(auth.getName());
        return user != null ? "user:" + user.getId() : "ip:" + ipUtils.clientIp();
    }

    /**
     * 本次请求申报的 User-Agent 原样，供访问日志落库与归类。只从请求头读，不做任何加工。
     * 请求上下文不在（非 HTTP 调用路径）时返回 null，isBot 对 null 判真，阅读不会计入
     */
    private String clientUserAgent() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getHeader("User-Agent") : null;
    }

    /**
     * 创建新文章
     * @param createDTO 文章创建 DTO
     * @return 新建文章 ID，供编辑器切换到更新路径
     */
    @Transactional
    public PostIdVO createPost(PostCreateDTO createDTO) {
        requireKnownStatus(createDTO.getStatus());
        Post post = postConverter.toEntity(createDTO);
        // 雪花 ID 在应用层生成：主键不暴露文章总数，也不给遍历留下可预测的序列（db-design.md §1.1）
        post.setId(IdUtil.getSnowflakeNextId());

        // 设置作者 ID
         String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
         com.ayun.entity.User user = userMapper.findByEmail(email);
         post.setAuthorId(user.getId());

        // 草稿免分类；对外发布（published / scheduled）必须已选分类
        requireCategoryWhenPublishing(post.getStatus(), post.getCategoryId());
        if ("published".equals(post.getStatus())) {
            post.setPublishedAt(LocalDateTime.now());
        } else if ("scheduled".equals(post.getStatus())) {
            post.setPublishedAt(requireFuturePublishTime(createDTO.getPublishTime()));
        }
        generateExcerptIfAbsent(post);
        postMapper.insert(post);

        // 处理标签（去 null、去重、校验存在性后再落关联）
        List<Integer> tagIds = requireKnownTags(createDTO.getTagIds());
        if (tagIds != null) {
            for (Integer tagId : tagIds) {
                tagMapper.insertPostTag(post.getId(), tagId);
            }
        }

        return PostIdVO.builder().id(post.getId()).build();
    }

    /**
     * 更新文章信息
     * @param id 文章 ID
     * @param updateDTO 文章更新 DTO
     */
    @Transactional
    public void updatePost(Long id, PostUpdateDTO updateDTO) {
        // 在改实体之前先认状态：后台的下线/重新上线走的就是这条 PUT，offline 必须在白名单里
        requireKnownStatus(updateDTO.getStatus());
        Post post = postMapper.findById(id);
        if (post == null) {
            throw new NotFoundException("文章不存在");
        }

        String oldStatus = post.getStatus();
        postConverter.updateEntity(updateDTO, post);
        String newStatus = post.getStatus();
        // 分类强制只作用于「转入对外发布」的变迁；存量 published/scheduled 行的纯内容保存不受影响。
        // offline 不在强制名单里：下线只是收回可见性，不该反过来要求一篇无分类的存量文章先补分类
        if (!newStatus.equals(oldStatus)) {
            requireCategoryWhenPublishing(newStatus, post.getCategoryId());
        }
        // 计划时刻只在请求方要求转 scheduled 时校验与写入：纯内容保存不带 status，不得改写既有计划时刻
        if ("scheduled".equals(updateDTO.getStatus())) {
            post.setPublishedAt(requireFuturePublishTime(updateDTO.getPublishTime()));
        } else if ("published".equals(newStatus) && post.getPublishedAt() == null) {
            // 由草稿/定时改为已发布且从未记过发布时间时，补写当前时间，避免前台「发布于」为空。
            // published → offline 不命中任何分支，offline → published 因 published_at 已有值也不命中，
            // 故下线与重新上线都沿用首次对外可见的时刻
            post.setPublishedAt(LocalDateTime.now());
        }
        // 下架留痕：只在「转入 offline」这一刻写入操作者与时刻，之后永不主动清空——
        // 把它放回去的人抹不掉「是谁、在什么时候撤下来的」这条记录
        if (!"offline".equals(oldStatus) && "offline".equals(newStatus)) {
            com.ayun.entity.User actor = currentUser();
            post.setOfflinedBy(actor.getId());
            post.setOfflinedAt(LocalDateTime.now());
        }
        generateExcerptIfAbsent(post);
        postMapper.update(post);

        // scheduled 撤下为 draft 时清空计划时刻，恢复「从未发布」语义；
        // 通用 update 的 <if> 跳过 null，清空必须走独立语句
        if ("scheduled".equals(oldStatus) && "draft".equals(newStatus)) {
            postMapper.clearPublishedAt(id);
        }

        if (updateDTO.getTagIds() != null) {
            tagMapper.deletePostTags(id);
            for (Integer tagId : requireKnownTags(updateDTO.getTagIds())) {
                tagMapper.insertPostTag(id, tagId);
            }
        }
    }

    /**
     * 标签入参治理：去 null、去重、校验存在性，返回治理后的列表（null 透传表示「本次不动标签」）。
     * 不治理的两个后果都在写库时才炸：重复 id 撞 post_tags 主键冒 500；
     * 不存在的 id 留孤儿关联（schema 对 post_tags 无外键，findByPostId 内连接后不可见）
     */
    private List<Integer> requireKnownTags(List<Integer> tagIds) {
        if (tagIds == null) {
            return null;
        }
        List<Integer> distinct = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty() && tagMapper.countExisting(distinct) != distinct.size()) {
            throw new BizException("包含不存在的标签");
        }
        return distinct;
    }

    /**
     * 摘要是正文的服务端派生物，作者侧没有输入口（business-model.md §2.3）：
     * 本次落库的目标状态为 published 或 scheduled、且这一行还没有摘要时，从正文生成一次。
     * 已有摘要就原样留着——已发布文章再改正文不重算，列表卡片与详情页 description 才不会随每次保存漂移。
     * 草稿永不生成（正文还在改，此刻的开头几句话不代表这篇文章）；
     * 下线也不生成，下线只是收回可见性，不该顺带改写对外文案
     */
    private void generateExcerptIfAbsent(Post post) {
        if (!"published".equals(post.getStatus()) && !"scheduled".equals(post.getStatus())) {
            return;
        }
        if (post.getExcerpt() != null && !post.getExcerpt().isBlank()) {
            return;
        }
        post.setExcerpt(ExcerptUtils.generate(post.getContent(), ExcerptUtils.MAX_LENGTH));
    }

    /**
     * 对外发布（published / scheduled）必须已选分类；草稿与已下线不要求
     */
    private void requireCategoryWhenPublishing(String status, Integer categoryId) {
        if (("published".equals(status) || "scheduled".equals(status)) && categoryId == null) {
            throw new BizException("发布或定时发布前请先选择文章分类");
        }
    }

    /**
     * 状态白名单：只认 posts.status 那四个 ENUM 值。过去非法值一路走到写库，由 MySQL 严格模式报错、
     * 对外冒成 500「服务暂时不可用」，调用方拿不到「是状态填错了」这句人话。
     * 不带状态即本次不改它（上下线之外的保存都走这条路），照常放行
     */
    private void requireKnownStatus(String status) {
        if (status != null && !ALLOWED_STATUS.contains(status)) {
            throw new BizException("非法的文章状态");
        }
    }

    /**
     * 删除文章：前台与后台共用的删除入口，级联清理统一走 cascadeDelete（business-model.md §5.7 第 7 条）
     * @param id 文章 ID
     */
    @Transactional
    public void deletePost(Long id) {
        if (postMapper.findById(id) == null) {
            throw new BizException("文章不存在");
        }
        cascadeDelete(id);
    }
}
