package com.ayun.service;

import com.ayun.entity.Post;
import com.ayun.mapper.PostMapper;
import com.ayun.model.vo.AboutContentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 三份爬虫直读产物的生成：格式合法（空库也要能解析）、只出已发布文章、特殊字符转义、
 * 绝对地址一律由站点域名配置拼出。这三份没有 HTML 可实测，断言就落在文本本身
 */
@ExtendWith(MockitoExtension.class)
class SeoPublicServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private SeoPublicService seoPublicService;

    /** 一篇 2026-10-01 12:00 更新、09:00 发布的文章 */
    private Post article(long id, String title, String excerpt) {
        return Post.builder()
                .id(id)
                .title(title)
                .excerpt(excerpt)
                .publishedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .createdAt(LocalDateTime.of(2026, 9, 30, 20, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 1, 12, 0))
                .build();
    }

    private void useConfiguredSite() {
        when(settingsService.getSiteUrl()).thenReturn("https://cloudlog.site/");
    }

    @Test
    @DisplayName("sitemap：首页 + 每篇已发布文章一条 url，lastmod 为带时区偏移的 ISO 8601")
    void sitemapListsHomeAndEveryPublishedPost() {
        useConfiguredSite();
        when(postMapper.findAllPublishedForSitemap())
                .thenReturn(List.of(article(11L, "甲篇", "甲的摘要"), article(12L, "乙篇", "乙的摘要")));

        String xml = seoPublicService.sitemap();

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), "声明必须在最前");
        assertTrue(xml.contains("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"));
        assertTrue(xml.contains("<loc>https://cloudlog.site/</loc>"), "配置的末尾斜杠要去掉，否则拼出双斜杠");
        assertTrue(xml.contains("<loc>https://cloudlog.site/articles/11</loc>"));
        assertTrue(xml.contains("<loc>https://cloudlog.site/articles/12</loc>"));
        // 本机时区不参与断言字符串（CI 与本地偏移不同），只核对形状：日期 + T + 时间 + 偏移
        assertTrue(xml.matches("(?s).*<lastmod>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}</lastmod>.*"),
                "lastmod 须为 W3C Datetime，实际是：" + xml);
        assertTrue(xml.trim().endsWith("</urlset>"));
    }

    @Test
    @DisplayName("sitemap：空库也是合法文档，根元素齐备且只有首页一条")
    void sitemapIsEmptyButWellFormedWithoutArticles() {
        useConfiguredSite();
        when(postMapper.findAllPublishedForSitemap()).thenReturn(List.of());

        String xml = seoPublicService.sitemap();

        assertEquals(1, xml.lines().filter(line -> line.contains("<url>")).count());
        assertFalse(xml.contains("<lastmod>"), "首页不申报没有可靠来源的 lastmod");
        assertTrue(xml.trim().endsWith("</urlset>"));
    }

    @Test
    @DisplayName("sitemap：更新时间为空的文章省略 lastmod 元素，而不是留一个空标签")
    void sitemapOmitsLastmodWhenMomentMissing() {
        useConfiguredSite();
        when(postMapper.findAllPublishedForSitemap())
                .thenReturn(List.of(Post.builder().id(13L).title("无时刻").build()));

        assertFalse(seoPublicService.sitemap().contains("<lastmod>"));
    }

    @Test
    @DisplayName("sitemap：JVM 跑 UTC 时 lastmod 申报的仍是 +08:00，而不是把北京墙钟当成 UTC 时刻")
    void sitemapLastmodAnchorsToBusinessZone() {
        useConfiguredSite();
        when(postMapper.findAllPublishedForSitemap()).thenReturn(List.of(article(11L, "甲篇", "甲的摘要")));

        JvmClockProbe.underUtc(() -> {
            String xml = seoPublicService.sitemap();
            // 列值 12:00 是北京时间；偏移报成 Z 就等于向爬虫申报 20:00 这个瞬时，整份 sitemap 早 8 小时
            assertTrue(xml.contains("<lastmod>2026-10-01T12:00:00+08:00</lastmod>"),
                    "lastmod 的偏移须与列值口径同基，实际是：" + xml);
        });
    }

    @Test
    @DisplayName("feed：JVM 跑 UTC 时 pubDate 换算到 GMT 报的仍是同一个瞬时")
    void feedPubDateAnchorsToBusinessZone() {
        useConfiguredSite();
        when(settingsService.getSiteName()).thenReturn("CloudLog");
        when(settingsService.getAboutContent()).thenReturn(AboutContentVO.builder().lead("导语").build());
        when(postMapper.findLatestPublishedForFeed(20)).thenReturn(List.of(article(21L, "一篇", "一段摘要")));

        JvmClockProbe.underUtc(() -> {
            String xml = seoPublicService.feed();
            // 09:00 北京 = 01:00 GMT；按运行时时区读就会把 09:00 直接当成 GMT
            assertTrue(xml.contains("<pubDate>Thu, 1 Oct 2026 01:00:00 GMT</pubDate>"),
                    "pubDate 须由北京墙钟换算到 GMT，实际是：" + xml);
        });
    }

    @Test
    @DisplayName("robots：放行根路径、禁抓后台与账号簇，末尾申报站点地图地址")
    void robotsAllowsRootAndDisallowsPrivateAreas() {
        useConfiguredSite();

        String txt = seoPublicService.robots();

        assertTrue(txt.startsWith("User-agent: *\nAllow: /\n"));
        List.of("/admin", "/editor", "/drafts", "/login", "/register", "/forgot-password", "/profile", "/settings")
                .forEach(path -> assertTrue(txt.contains("Disallow: " + path + "\n"), "缺 " + path));
        assertTrue(txt.contains("Sitemap: https://cloudlog.site/sitemap.xml"));
    }

    @Test
    @DisplayName("feed：channel 带站点名 / 链接 / 导语，item 带 title link guid pubDate 与 CDATA 摘要")
    void feedCarriesChannelAndItemFields() {
        useConfiguredSite();
        when(settingsService.getSiteName()).thenReturn("CloudLog");
        when(settingsService.getAboutContent()).thenReturn(AboutContentVO.builder().lead("主要写给自己看。").build());
        when(postMapper.findLatestPublishedForFeed(20)).thenReturn(List.of(article(21L, "一篇", "一段摘要")));

        String xml = seoPublicService.feed();

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\">"));
        assertTrue(xml.contains("<title>CloudLog</title>"));
        assertTrue(xml.contains("<link>https://cloudlog.site/</link>"));
        assertTrue(xml.contains("<description>主要写给自己看。</description>"));
        assertTrue(xml.contains("<language>zh-CN</language>"));
        assertTrue(xml.contains("<link>https://cloudlog.site/articles/21</link>"));
        assertTrue(xml.contains("<guid isPermaLink=\"true\">https://cloudlog.site/articles/21</guid>"));
        assertTrue(xml.matches("(?s).*<pubDate>[A-Z][a-z]{2}, \\d{1,2} [A-Z][a-z]{2} \\d{4} .* GMT</pubDate>.*"),
                "pubDate 须为 RFC 1123 且落在 GMT（日位不补零），实际是：" + xml);
        assertTrue(xml.contains("<description><![CDATA[一段摘要]]></description>"));
        assertTrue(xml.trim().endsWith("</rss>"));
    }

    @Test
    @DisplayName("feed：空库仍有完整 channel，零个 item")
    void feedIsValidWithoutArticles() {
        useConfiguredSite();
        when(settingsService.getSiteName()).thenReturn("CloudLog");
        when(settingsService.getAboutContent()).thenReturn(AboutContentVO.builder().lead("导语").build());
        when(postMapper.findLatestPublishedForFeed(20)).thenReturn(List.of());

        String xml = seoPublicService.feed();

        assertFalse(xml.contains("<item>"));
        assertTrue(xml.contains("<title>CloudLog</title>"));
        assertTrue(xml.trim().endsWith("</rss>"));
    }

    @Test
    @DisplayName("feed：存量手工发布的行没有 published_at，pubDate 回落到创建时刻")
    void feedFallsBackToCreatedAtForMomentlessRows() {
        useConfiguredSite();
        when(settingsService.getSiteName()).thenReturn("CloudLog");
        when(settingsService.getAboutContent()).thenReturn(AboutContentVO.builder().lead("导语").build());
        when(postMapper.findLatestPublishedForFeed(20)).thenReturn(List.of(
                Post.builder().id(22L).title("无发布时刻").createdAt(LocalDateTime.of(2026, 9, 30, 20, 0)).build()));

        String xml = seoPublicService.feed();

        assertTrue(xml.contains("<pubDate>"), "该回落创建时刻而不是整个省掉，订阅器按 pubDate 排序");
        assertFalse(xml.contains("<description><![CDATA[null") , "摘要为 null 时 CDATA 里是空段而不是字面量 null");
    }

    @Test
    @DisplayName("转义：& < > \" ' 都换成实体，CDATA 里的 ]]> 断成两段")
    void escapesXmlSpecialCharacters() {
        assertEquals("A &amp; B &lt;p&gt; &quot;q&quot; &apos;z&apos;", SeoPublicService.escapeXml("A & B <p> \"q\" 'z'"));
        assertEquals("", SeoPublicService.escapeXml(null));
        assertEquals("前]]]]><![CDATA[>后", SeoPublicService.cdataSafe("前]]>后"));
        assertEquals("", SeoPublicService.cdataSafe(null));
    }

    @Test
    @DisplayName("站点域名未配置且拿不到请求上下文时不编造主机，链接退化为站内绝对路径")
    void blankSiteUrlWithoutRequestYieldsRelativeLinks() {
        when(settingsService.getSiteUrl()).thenReturn("");
        when(postMapper.findAllPublishedForSitemap()).thenReturn(List.of(Post.builder().id(23L).build()));

        String xml = seoPublicService.sitemap();

        assertTrue(xml.contains("<loc>/articles/23</loc>"), "宁缺不假：没有上下文就照实给出无前缀地址");
    }
}
