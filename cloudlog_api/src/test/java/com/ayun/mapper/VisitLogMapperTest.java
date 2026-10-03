package com.ayun.mapper;

import com.ayun.entity.VisitLog;
import com.ayun.model.vo.VisitLogItemVO;
import com.ayun.model.vo.VisitLogSummaryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * visit_logs 的 SQL 契约：两条窗口在同一条语句里分侧计数、日/小时两种分桶、孤儿文章与未分类那一格、
 * 四个可组合筛选、以及保留期删除的谓词边界。这些都写在 XML 里，mock 出不来，只能对真库跑。
 *
 * <p>用例数据只用 {@code 240.0.0.0/8}（保留段，真实访客不可能出现在这个前缀上）与一个合成 post_id。
 * 整类跑在一个会回滚的事务里：其中一条用例会执行保留期那条范围 DELETE，回滚保证它不碰到别人的行
 */
@SpringBootTest
@Transactional
class VisitLogMapperTest {

    private static final String TEST_IP_PREFIX = "240.0.";

    /** 库里不可能有这一天，窗口内的行数因此完全由本用例自己插的行决定 */
    private static final LocalDate TEST_DAY = LocalDate.of(2026, 9, 20);

    /** posts 里没有这一篇：Top 文章的左连、未分类那一格都靠它验 */
    private static final long ORPHAN_POST = 7123456789012345602L;

    @Autowired
    private VisitLogMapper visitLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 库里真实存在的一篇已发布文章（带分类），取现值作为期望，不写死 */
    private record PublishedPost(long id, String title, String categoryName) {
    }

    private PublishedPost anyPublishedPostWithCategory() {
        List<PublishedPost> found = jdbcTemplate.query(
                "SELECT p.id, p.title, c.name FROM posts p JOIN categories c ON c.id = p.category_id "
                        + "WHERE p.status = 'published' LIMIT 1",
                (rs, n) -> new PublishedPost(rs.getLong(1), rs.getString(2), rs.getString(3)));
        return found.isEmpty() ? null : found.get(0);
    }

    private void insertRow(Long postId, String ip, String userAgent, LocalDateTime at) {
        jdbcTemplate.update("INSERT INTO visit_logs (post_id, ip, user_agent, created_at) VALUES (?, ?, ?, ?)",
                postId, ip, userAgent, at);
    }

    /**
     * 当日四行：同一篇文章两次、一篇不存在的文章一次、post_id 为空一次——分别覆盖「有标题」「文章已删」「非文章页访问」
     */
    private void seedTestDay(PublishedPost post) {
        insertRow(post.id(), TEST_IP_PREFIX + "1.1", "Mozilla/5.0 (Macintosh) Chrome/120.0.0.0 Safari/537.36", TEST_DAY.atTime(10, 5));
        insertRow(post.id(), TEST_IP_PREFIX + "2.2", "Mozilla/5.0 (Windows) Chrome/120.0.0.0 Safari/537.36", TEST_DAY.atTime(10, 40));
        insertRow(ORPHAN_POST, TEST_IP_PREFIX + "3.3", "Mozilla/5.0 (Macintosh) Version/17.0 Safari/605.1.15", TEST_DAY.atTime(11, 2));
        insertRow((Long) null, TEST_IP_PREFIX + "4.4", "Mozilla/5.0 (X11; rv:121.0) Gecko/20100101 Firefox/121.0", TEST_DAY.atTime(12, 15));
    }

    @Test
    @DisplayName("insert 不写 created_at，时刻由库默认值补上")
    void insertLeavesCreatedAtToTheDatabase() {
        visitLogMapper.insert(VisitLog.builder()
                .postId(ORPHAN_POST).ip(TEST_IP_PREFIX + "7.7").userAgent("Mozilla/5.0 Chrome/120.0.0.0").build());

        LocalDateTime stored = jdbcTemplate.queryForObject(
                "SELECT created_at FROM visit_logs WHERE ip = ?", LocalDateTime.class, TEST_IP_PREFIX + "7.7");
        assertNotNull(stored);
        assertTrue(stored.isAfter(LocalDateTime.now().minusMinutes(1)), "created_at 应当是刚刚，实际为 " + stored);
    }

    @Test
    @DisplayName("两条窗口在同一条语句里分侧计数：本窗口的 PV / UV / 被访问过的文章数与前一窗口的 PV 各归各")
    void countWindowSplitsCurrentAndPreviousWindowInOneScan() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);
        insertRow(post.id(), TEST_IP_PREFIX + "9.9", "Mozilla/5.0 Chrome/120.0.0.0", TEST_DAY.minusDays(1).atTime(23, 59));

        VisitLogSummaryVO.WindowCount counted = visitLogMapper.countWindow(
                TEST_DAY.minusDays(1).atStartOfDay(), TEST_DAY.atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay());

        assertEquals(4L, counted.getPv().longValue());
        assertEquals(4L, counted.getUv().longValue());
        // post_id 为空的那一行不计入文章数：COUNT(DISTINCT post_id) 天然忽略 NULL
        assertEquals(2L, counted.getPostCount().longValue());
        assertEquals(1L, counted.getPrevPv().longValue());
    }

    @Test
    @DisplayName("趋势两种分桶都算得出：按小时聚到当日之内，按日跨天分行")
    void trendGroupsByHourOrByDay() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);

        List<VisitLogSummaryVO.TrendCount> hourly =
                visitLogMapper.findTrend(TEST_DAY.atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay(), true);
        assertEquals(List.of("10", "11", "12"),
                hourly.stream().map(VisitLogSummaryVO.TrendCount::getBucket).toList());
        assertEquals(2L, hourly.get(0).getVisitCount().longValue());

        insertRow(post.id(), TEST_IP_PREFIX + "6.6", "Mozilla/5.0 Chrome/120.0.0.0", TEST_DAY.minusDays(1).atTime(9, 0));
        List<VisitLogSummaryVO.TrendCount> daily =
                visitLogMapper.findTrend(TEST_DAY.minusDays(1).atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay(), false);
        assertEquals(2, daily.size());
        assertEquals(TEST_DAY.minusDays(1).toString(), daily.get(0).getBucket());
        assertEquals(TEST_DAY.toString(), daily.get(1).getBucket());
    }

    @Test
    @DisplayName("小时桶只收窗口之内：昨天那一小时不会算进今天的柱子")
    void hourlyBucketsStayInsideTheWindow() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);
        insertRow(post.id(), TEST_IP_PREFIX + "6.6", "Mozilla/5.0 Chrome/120.0.0.0", TEST_DAY.minusDays(1).atTime(10, 30));

        List<VisitLogSummaryVO.TrendCount> hourly =
                visitLogMapper.findTrend(TEST_DAY.atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay(), true);

        assertEquals(3, hourly.size());
        assertEquals(4L, hourly.stream().mapToLong(row -> row.getVisitCount().longValue()).sum());
    }

    @Test
    @DisplayName("Top 文章左连 posts：真文章带标题，库里没这篇的访问仍留在榜上且标题为空")
    void topPostsKeepRowsWhoseArticleIsGone() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);

        List<VisitLogSummaryVO.TopPost> top =
                visitLogMapper.findTopPosts(TEST_DAY.atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay(), 10);

        VisitLogSummaryVO.TopPost first = top.get(0);
        assertEquals(post.id(), first.getPostId().longValue());
        assertEquals(post.title(), first.getTitle());
        assertEquals(2L, first.getVisitCount().longValue());
        assertTrue(top.stream().anyMatch(row -> Objects.equals(ORPHAN_POST, row.getPostId()) && row.getTitle() == null),
                "已删除文章那一行应当留在榜上");
        assertTrue(top.stream().anyMatch(row -> row.getPostId() == null), "post_id 为空的那一格也要在榜上");
    }

    @Test
    @DisplayName("Top 分类两次左连：文章已删、本就无分类都落进分类名为空的那一格")
    void topCategoriesFoldOrphansIntoOneBucket() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);

        List<VisitLogSummaryVO.TopCategory> top =
                visitLogMapper.findTopCategories(TEST_DAY.atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay(), 10);

        VisitLogSummaryVO.TopCategory named = top.stream()
                .filter(row -> post.categoryName().equals(row.getCategoryName()))
                .findFirst().orElseThrow(() -> new AssertionError("该文章所属分类那一格不见了：" + top));
        assertEquals(2L, named.getVisitCount().longValue());

        VisitLogSummaryVO.TopCategory unnamed = top.stream()
                .filter(row -> row.getCategoryName() == null)
                .findFirst().orElseThrow(() -> new AssertionError("未分类那一格不见了：" + top));
        // 合成 post_id 在 posts 里不存在、以及 post_id 本身为空，两条路径都并到这里
        assertEquals(2L, unnamed.getVisitCount().longValue());
        assertEquals(4L, top.stream().mapToLong(row -> row.getVisitCount().longValue()).sum());
    }

    @Test
    @DisplayName("地址分组不带 LIMIT：同一 /16 的几行原样返回，合并留给服务层在脱敏之后做")
    void ipGroupingReturnsEveryAddress() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);

        List<VisitLogSummaryVO.IpCount> rows =
                visitLogMapper.countByIp(TEST_DAY.atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay());

        assertEquals(4, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getIp().startsWith(TEST_IP_PREFIX)));
    }

    @Test
    @DisplayName("明细四个条件可组合；地址前缀模式与 UA 关键词里的通配符都按字面量处理")
    void detailFiltersCombine() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);
        // 只为区分 240.% 与 240.0.% 两档前缀而存在的第五行，挂在另一篇文章上，免得干扰按文章筛的计数
        insertRow(ORPHAN_POST, "240.1.1.1", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Version/17.0 Mobile Safari/604.1",
                TEST_DAY.atTime(13, 0));
        LocalDateTime start = TEST_DAY.atStartOfDay();
        LocalDateTime end = TEST_DAY.plusDays(1).atStartOfDay();

        assertEquals(5, visitLogMapper.findAdminList(start, end, null, "240.%", null).size());
        assertEquals(4, visitLogMapper.findAdminList(start, end, null, "240.0.%", null).size());
        assertEquals(2, visitLogMapper.findAdminList(start, end, post.id(), null, null).size());
        assertEquals(1, visitLogMapper.findAdminList(start, end, null, null, "Firefox").size());
        assertEquals(2, visitLogMapper.findAdminList(start, end, post.id(), null, "Chrome").size());
        assertEquals(1, visitLogMapper.findAdminList(start, end, post.id(), "240.0.2.%", "Chrome").size());
        // % 是用户输入的通配符，被转义后不该当成 LIKE 运算符去匹配 Chrome
        assertEquals(0, visitLogMapper.findAdminList(start, end, null, null, "Chro%e").size());
    }

    @Test
    @DisplayName("明细行把原始地址与标题一起带回来，供服务层脱敏与归类")
    void detailRowCarriesRawFieldsForMasking() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);

        VisitLogItemVO.VisitRow row = visitLogMapper.findAdminList(
                TEST_DAY.atStartOfDay(), TEST_DAY.plusDays(1).atStartOfDay(), null, "240.0.1.%", null).get(0);

        assertEquals(TEST_IP_PREFIX + "1.1", row.getIp());
        assertNotNull(row.getCreatedAt());
        assertEquals(post.id(), row.getPostId().longValue());
        assertEquals(post.title(), row.getPostTitle());
    }

    @Test
    @DisplayName("保留期：只删阈值之前的行，窗口内的与差一天到期的都不动")
    void retentionDeletesOnlyExpiredRows() {
        PublishedPost post = requirePublishedPost();
        seedTestDay(post);
        insertRow(post.id(), TEST_IP_PREFIX + "5.5", "Mozilla/5.0 Chrome/120.0.0.0", LocalDateTime.now().minusDays(181));
        insertRow(post.id(), TEST_IP_PREFIX + "4.5", "Mozilla/5.0 Chrome/120.0.0.0", LocalDateTime.now().minusDays(179));

        int removed = visitLogMapper.deleteCreatedBefore(LocalDateTime.now().minusDays(180));

        assertTrue(removed >= 1, "应当至少删掉本用例那一行超期记录");
        assertEquals(0, countTestRowsBefore(LocalDateTime.now().minusDays(180)));
        assertEquals(1, countTestRowsAt(TEST_IP_PREFIX + "4.5"));
        assertEquals(4, countTestRowsFrom(TEST_DAY.atStartOfDay()));
    }

    private PublishedPost requirePublishedPost() {
        PublishedPost post = anyPublishedPostWithCategory();
        assertNotNull(post, "本地开发库里需要至少一篇带分类的已发布文章才能跑这组 SQL 契约");
        return post;
    }

    private int countTestRowsBefore(LocalDateTime threshold) {
        return requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visit_logs WHERE ip LIKE ? AND created_at < ?",
                Integer.class, TEST_IP_PREFIX + "%", threshold));
    }

    private int countTestRowsAt(String ip) {
        return requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visit_logs WHERE ip = ?", Integer.class, ip));
    }

    private int countTestRowsFrom(LocalDateTime from) {
        return requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM visit_logs WHERE ip LIKE ? AND created_at >= ?",
                Integer.class, TEST_IP_PREFIX + "%", from));
    }

    private static int requireNonNull(Integer value) {
        return Objects.requireNonNull(value);
    }
}
