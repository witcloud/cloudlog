package com.ayun.controller;

import com.ayun.service.SeoPublicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 爬虫直读的三份静态产物（无需登录，SecurityConfig 按精确路径放行）。
 * 响应是裸的 XML / 纯文本，不套 {@code Result} 信封——这两格式的读者是爬虫与订阅器，
 * 它们按协议解析文档根节点，套一层 JSON 就等于没输出
 */
@Tag(name = "SEO 产物", description = "robots.txt / sitemap.xml / RSS 订阅源的公开读取")
@RestController
public class SeoPublicController {

    private static final String XML_UTF8 = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8";

    private static final String TEXT_UTF8 = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8";

    @Autowired
    private SeoPublicService seoPublicService;

    @Operation(summary = "站点地图", description = "首页与全部已发布文章的详情页地址，lastmod 取文章的更新时间；"
            + "分类与标签是查询参数路由，按 sitemaps.org 惯例不申报")
    @GetMapping(value = "/sitemap.xml", produces = XML_UTF8)
    public String sitemap() {
        return seoPublicService.sitemap();
    }

    @Operation(summary = "爬虫规则", description = "放行根路径、禁抓后台与账号簇，并申报站点地图地址")
    @GetMapping(value = "/robots.txt", produces = TEXT_UTF8)
    public String robots() {
        return seoPublicService.robots();
    }

    @Operation(summary = "RSS 订阅源", description = "最新 20 篇已发布文章，按发布时间倒序；"
            + "description 为服务端从正文派生的摘要，用 CDATA 包裹")
    @GetMapping(value = "/feed.xml", produces = XML_UTF8)
    public String feed() {
        return seoPublicService.feed();
    }
}
