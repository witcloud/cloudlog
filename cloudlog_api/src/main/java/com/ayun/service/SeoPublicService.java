package com.ayun.service;

import com.ayun.entity.Post;
import com.ayun.mapper.PostMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 服务端直出的 SEO 产物：robots.txt、sitemap.xml、RSS 2.0 订阅源。
 * 这三份是纯文本 / XML，不依赖浏览器执行 JS，任何爬虫都读得到——与运行时注入的 meta 分属两条路
 * （business-model.md §2.10 末段的局限、§6「不做 SSR」）。本站是无 SSR 的单页应用，
 * 这三份能给的只有「有哪些文章存在、什么时候改过」，给不了正文渲染结果
 */
@Service
public class SeoPublicService {

    /** RSS 只出最新若干篇，不给全站归档 */
    private static final int FEED_ITEM_LIMIT = 20;

    /** sitemap 的 lastmod 用 W3C Datetime（ISO 8601 带时区偏移），Google 认这一种 */
    private static final DateTimeFormatter W3C_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** RSS 的 pubDate 按 RFC 822/1123 且必须换算到 GMT */
    private static final DateTimeFormatter RSS_DATE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME;

    /** robots.txt 里禁抓的路径：后台、编辑器、草稿箱与账号簇，都是读者不该进、进去也是空壳的页面 */
    private static final List<String> DISALLOWED_PATHS = List.of(
            "/admin", "/editor", "/drafts", "/login", "/register", "/forgot-password", "/profile", "/settings");

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private SettingsService settingsService;

    /**
     * 站点地图：首页 + 全部已发布文章的详情页。分类与标签页是查询参数路由
     * （{@code /?category=1}、{@code /?tag=2}），按 sitemaps.org 的惯例不带——
     * 带上去等于把同一批文章在不同参数下重复申报
     */
    public String sitemap() {
        String origin = siteOrigin();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
                // 首页没有可靠的「最后修改时刻」，只申报 loc：lastmod 在协议里是可选项
                .append("  <url><loc>").append(escapeXml(origin + "/")).append("</loc></url>\n");
        for (Post post : postMapper.findAllPublishedForSitemap()) {
            xml.append("  <url>\n")
                    .append("    <loc>").append(escapeXml(origin + "/articles/" + post.getId())).append("</loc>\n");
            String lastmod = formatW3cDateTime(post.getUpdatedAt());
            if (lastmod != null) {
                xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
            }
            xml.append("  </url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    /**
     * robots.txt。这里给的是**域名根**上的路径与地址，而应用挂在 {@code /api/v1} 之下
     * （server.servlet.context-path），所以这三份产物要靠反向代理把根路径映射过来，
     * 写法见 README「SEO 产物与反向代理」一节
     */
    public String robots() {
        StringBuilder txt = new StringBuilder("User-agent: *\nAllow: /\n");
        DISALLOWED_PATHS.forEach(path -> txt.append("Disallow: ").append(path).append('\n'));
        return txt.append('\n').append("Sitemap: ").append(siteOrigin()).append("/sitemap.xml\n").toString();
    }

    /**
     * RSS 2.0：最新 {@value #FEED_ITEM_LIMIT} 篇已发布文章，按发布时间倒序。
     * channel 的描述取关于页导语（{@code site_settings.about_lead}）——两处答的都是
     * 「这个站是记什么的」，不在第三处重抄一遍措辞（改一处漂一处的教训见 {@code utils/seo.ts}）
     */
    public String feed() {
        String origin = siteOrigin();
        String siteName = settingsService.getSiteName();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<rss version=\"2.0\">\n  <channel>\n")
                .append("    <title>").append(escapeXml(siteName)).append("</title>\n")
                .append("    <link>").append(escapeXml(origin + "/")).append("</link>\n")
                .append("    <description>").append(escapeXml(settingsService.getAboutContent().getLead()))
                .append("</description>\n")
                .append("    <language>zh-CN</language>\n");
        for (Post post : postMapper.findLatestPublishedForFeed(FEED_ITEM_LIMIT)) {
            String link = origin + "/articles/" + post.getId();
            xml.append("    <item>\n")
                    .append("      <title>").append(escapeXml(post.getTitle())).append("</title>\n")
                    .append("      <link>").append(escapeXml(link)).append("</link>\n")
                    // guid 直接用详情页地址：文章 id 是雪花值，本身就已经唯一且不可变
                    .append("      <guid isPermaLink=\"true\">").append(escapeXml(link)).append("</guid>\n");
            String pubDate = formatRssDate(post.getPublishedAt() != null
                    ? post.getPublishedAt() : post.getCreatedAt());
            if (pubDate != null) {
                xml.append("      <pubDate>").append(pubDate).append("</pubDate>\n");
            }
            xml.append("      <description><![CDATA[")
                    .append(cdataSafe(post.getExcerpt()))
                    .append("]]></description>\n")
                    .append("    </item>\n");
        }
        return xml.append("  </channel>\n</rss>\n").toString();
    }

    /**
     * 站点对外根地址（含协议、末尾不带斜杠）。取不到配置时按当次请求的协议 + 主机兜底：
     * 本机自测直接得到 localhost 地址，部署后不必先填一项才能启动；
     * 但生产要给出稳定可比的绝对地址仍应在后台「系统设置 → 站点域名」填一次，
     * 兜底值会随请求头里的 Host 变，不该长期当订阅源里的锚点
     */
    private String siteOrigin() {
        String configured = settingsService.getSiteUrl();
        if (configured != null && !configured.isBlank()) {
            return stripTrailingSlash(configured.trim());
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return stripTrailingSlash(ServletUriComponentsBuilder.fromRequestUri(request)
                    .replacePath(null)
                    .replaceQuery(null)
                    .build()
                    .toUriString());
        }
        // 没有请求上下文（例如单元测试直接调）时无从猜测地址，宁可让链接缺协议也不要编一个假的域名
        return "";
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * XML 文本转义。& 必须最先换，否则会把后面几个替换造出来的 &lt; 再转一层变成 &amp;lt;。
     * 标题与摘要里都可能带 &（比如「Java & Go」）与 <（比较式、HTML 片段），不转义就是一份解析不了的 XML
     */
    static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** CDATA 里不需要实体转义，但序列 {@code ]]>} 会提前结束这段，出现就把它断成两段 */
    static String cdataSafe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("]]>", "]]]]><![CDATA[>");
    }

    /** ISO 8601 带本地时区偏移；时刻为空即返回 null，由调用方省略整个元素 */
    private String formatW3cDateTime(LocalDateTime moment) {
        return moment == null ? null : W3C_DATETIME.format(moment.atZone(ZoneId.systemDefault()));
    }

    /** RSS 的日期必须落在 GMT 上，按运行时时区换算过去 */
    private String formatRssDate(LocalDateTime moment) {
        if (moment == null) {
            return null;
        }
        return RSS_DATE_TIME.format(moment.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC));
    }
}
