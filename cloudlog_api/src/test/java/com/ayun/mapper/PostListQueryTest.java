package com.ayun.mapper;

import com.ayun.entity.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 列表查询的列裁剪：正文是 LONGTEXT 列，列表把它从 SELECT 里去掉，但其余列与排序照旧要全。
 * 这条只能对真库跑——列清单是 SQL 里的事，mock 不出来
 */
@SpringBootTest
@Transactional
class PostListQueryTest {

    private static final long SEEDED_POST = 7123456789012345699L;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("findAllBrief 返回的行不带正文，标题/分类/三个计数照常映射")
    void listQueryDropsContent() {
        List<Post> list = postMapper.findAllBrief(null, null, null, null, "latest");

        for (Post post : list) {
            assertNull(post.getContent(), "列表不该把 LONGTEXT 正文读出来：id=" + post.getId());
            assertNotNull(post.getTitle(), "瘦身只去掉正文，标题仍要在：id=" + post.getId());
            assertNotNull(post.getStatus(), "状态是前端打徽标用的，不能一起被裁掉：id=" + post.getId());
            assertNotNull(post.getViewCount(), "阅读量供列表行展示：id=" + post.getId());
        }
        // 空结果会让上面的循环空跑，这条断言保证用例真的取到了已发布文章
        assertFalse(list.isEmpty(), "本地库有已发布文章，列表查询不该返回空");
    }

    @Test
    @DisplayName("带关键词查询时谓词段照旧生效，命中结果同样不带正文")
    void keywordSearchStillFiltersInSql() {
        List<Post> recent = postMapper.findAllBrief(null, null, null, null, "latest");
        assertFalse(recent.isEmpty(), "本地库没有已发布文章，关键词分支无从验证");

        String keyword = recent.get(0).getTitle().substring(0, 2);
        List<Post> matched = postMapper.findAllBrief(null, null, null, keyword, "latest");

        assertFalse(matched.isEmpty(), "标题里的词应能命中文章本身，实际搜的是「" + keyword + "」");
        assertNull(matched.get(0).getContent(), "搜索结果同样不带正文");
    }

    @Test
    @DisplayName("单字关键词退回 LIKE：ngram 词元是两个字符，MATCH 对单字必然 0 命中")
    void singleCharKeywordFallsBackToLike() {
        List<Post> recent = postMapper.findAllBrief(null, null, null, null, "latest");
        assertFalse(recent.isEmpty(), "本地库该有已发布文章");
        String oneChar = recent.get(0).getTitle().substring(0, 1);

        List<Post> matched = postMapper.findAllBrief(null, null, null, oneChar, "latest");

        assertFalse(matched.isEmpty(), "单字搜索必须由 LIKE 兜住，实际搜的是「" + oneChar + "」");
    }

    @Test
    @DisplayName("含英文的关键词退回 LIKE：标题里的「Mac」必须搜得到——ngram 停用词把 a/i 当分隔符，MATCH 分支必然 0 命中")
    void asciiKeywordFallsBackToLike() {
        // 造数自带 Mac，不依赖本地库碰巧有一篇英文标题的文章；事务回滚，不污染真实数据
        jdbcTemplate.update(
                "INSERT INTO posts (id, title, content, author_id, status) VALUES (?, ?, ?, ?, 'published')",
                SEEDED_POST, "我的 Mac 开发环境配置", "正文里也写到了 macOS 的日常使用。", 1800000000000000001L);

        List<Post> matched = postMapper.findAllBrief(null, null, null, "Mac", "latest");

        assertTrue(matched.stream().anyMatch(post -> post.getId() == SEEDED_POST),
                "搜「Mac」必须命中标题含 Mac 的文章，实际命中 " + matched.size() + " 篇："
                        + matched.stream().map(Post::getTitle).toList());
        assertNull(matched.get(0).getContent(), "搜索结果同样不带正文");
    }

    @Test
    @DisplayName("关键词里的 % 按字面量匹配：转义失效时单字符 % 会把全部已发布文章都命中")
    void percentInKeywordIsNotAWildcard() {
        long published = postMapper.countPublished();
        assertTrue(published > 0, "本地库该有已发布文章，否则这条断言空跑");

        List<Post> hits = postMapper.findAllBrief(null, null, null, "%", "latest");

        assertTrue(hits.size() < published,
                "「%」若仍被当成通配符，模式串就是 %%%，" + published + " 篇会全部命中；"
                        + "转义生效后只该剩字面含 % 的那几篇，实际 " + hits.size() + " 篇");
    }

    @Test
    @DisplayName("草稿箱、我的文章、点赞过的、后台文章四个列表共用同一份取列清单，也不取正文")
    void otherListQueriesShareTheTrimmedColumns() {
        // 本地库里作者 1 既有草稿也有已发布文章，还赞过文章：四条查询都有行可断言
        List<Post> drafts = postMapper.findDrafts(1L, null, null, null, null);
        List<Post> mine = postMapper.findMine(1L, null, null, null);
        List<Post> liked = postMapper.findLikedByUser(1L);
        List<Post> admin = postMapper.findAdminAll(null, null, null, null, null, null);
        assertFalse(drafts.isEmpty() && mine.isEmpty() && liked.isEmpty() && admin.isEmpty(),
                "四条查询全部空跑，这条用例就没意义了");

        for (List<Post> rows : List.of(drafts, mine, liked, admin)) {
            for (Post post : rows) {
                assertNull(post.getContent(), "列表不该把正文读出来：id=" + post.getId());
                assertNotNull(post.getTitle(), "取列只裁掉了正文，标题仍在：id=" + post.getId());
                assertNotNull(post.getUpdatedAt(), "草稿箱、我的文章与后台列表都按修改时刻排序：id=" + post.getId());
            }
        }
    }

    @Test
    @DisplayName("删除前的属主确认同样不取正文")
    void ownershipCheckAlsoDropsContent() {
        List<Post> drafts = postMapper.findDrafts(1L, null, null, null, null);
        assertFalse(drafts.isEmpty(), "本地库该有草稿，否则这条用例是空跑");

        Post owned = postMapper.findOwnedDraft(drafts.get(0).getId(), 1L);

        assertNotNull(owned, "属主与状态都在 SQL 谓词里，按 (草稿 ID, 作者 1) 应当命中");
        assertNull(owned.getContent(), "级联删除前的确认只用得上「这一行在不在」，不该读 LONGTEXT");
    }
}
