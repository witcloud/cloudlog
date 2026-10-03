package com.ayun.mapper;

import com.ayun.entity.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEO 产物那两条查询的列裁剪与口径：只能对真库跑——少写一列、谓词写歪一档，
 * mock 出来的服务单测照样绿，浏览器里只会看到一份缺 lastmod 或条目为空的站点地图。
 * 全程只读，不写任何一行
 */
@SpringBootTest
class SeoPublicQueryTest {

    @Autowired
    private PostMapper postMapper;

    @Test
    @DisplayName("sitemap 查询的行数与 countPublished 同口径，且每行都带 lastmod 要用的时刻")
    void sitemapRowsMatchPublishedCount() {
        List<Post> rows = postMapper.findAllPublishedForSitemap();

        assertFalse(rows.isEmpty(), "本地库有已发布文章，sitemap 查询不该返回空");
        assertEquals(postMapper.countPublished(), rows.size(),
                "sitemap 收录的篇数必须等于后台统计的已发布数，两处口径不能各说一套");
        for (Post row : rows) {
            assertNotNull(row.getId(), "地址要由 id 拼：缺它就拼不出 <loc>");
            assertNotNull(row.getUpdatedAt(), "lastmod 取自这一列，为空只会让整篇从 sitemap 里少一个时间戳");
            assertNull(row.getContent(), "这条查询只要定位与时刻，不该把 LONGTEXT 正文读出来");
        }
    }

    @Test
    @DisplayName("RSS 查询按 limit 截断、按发布时间倒序，行内带 title 与摘要")
    void feedRowsAreLatestNDescending() {
        List<Post> rows = postMapper.findLatestPublishedForFeed(3);

        assertTrue(rows.size() <= 3, "limit 没生效：取回 " + rows.size() + " 行");
        LocalDateTime previous = null;
        for (Post row : rows) {
            assertNotNull(row.getTitle(), "订阅项的标题");
            assertNotNull(row.getCreatedAt(), "存量行可能没有 published_at，pubDate 要回落到这一列");
            assertTrue(row.getExcerpt() == null || !row.getExcerpt().isBlank(),
                    "摘要列不该是空白串：空白会让 CDATA 里出现一段无内容，id=" + row.getId());
            if (row.getPublishedAt() != null && previous != null) {
                assertFalse(row.getPublishedAt().isAfter(previous),
                        "顺序应为发布时间倒序：" + previous + " 之后又出现 " + row.getPublishedAt());
            }
            if (row.getPublishedAt() != null) {
                previous = row.getPublishedAt();
            }
        }
    }
}
