package com.ayun.model.vo;

import com.ayun.entity.Tag;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章视图对象")
public class PostVO {

    @Schema(description = "文章ID（雪花 ID）")
    private Long id;

    @Schema(description = "文章标题")
    private String title;

    @Schema(description = "文章摘要")
    private String excerpt;

    /**
     * 正文只由详情查询取出，列表与热榜走 findAllBrief，这一列压根没读，自然也不该出现在响应里
     */
    @Schema(description = "文章正文内容，仅详情接口携带；列表接口不带这个字段")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String content;

    @Schema(description = "文章封面图")
    private String cover;

    /**
     * SEO 三元组，由服务端按回落链合成，库里没有可填的 SEO 表（business-model.md §2.10）：
     * 标题 = 文章标题 + 站点名后缀（站点名取 site_settings.site_name）、描述 = 文章摘要、
     * 关键词 = 标签名按「, 」拼接，无标签即为空。
     * 只有详情接口填这三列，列表与热榜不带——与 content 同一条理由，别把 null 键铺满列表响应
     */
    @Schema(description = "详情页标题：「文章标题 - 站点名」，站点名取系统设置；仅详情接口携带")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String seoTitle;

    @Schema(description = "详情页描述：即文章摘要（服务端由正文派生）；仅详情接口携带")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String seoDescription;

    @Schema(description = "详情页关键词：该文章标签名按逗号拼接，无标签即为空、不写占位文案；仅详情接口携带")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String seoKeywords;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "作者信息")
    private AuthorVO author;

    @Schema(description = "分类信息")
    private CategoryVO category;

    @Schema(description = "标签列表")
    private List<Tag> tags;

    @Schema(description = "阅读量")
    private Integer views;

    @Schema(description = "点赞数")
    private Integer likes;

    @Schema(description = "当前浏览者是否已点赞，匿名与未登录恒为 false")
    private Boolean liked;

    @Schema(description = "评论数")
    private Integer comments;

    @Schema(description = "是否置顶")
    private Integer isTop;

    @Schema(description = "置顶排序权重，越大越靠前；仅 isTop=1 时参与排序")
    private Integer topOrder;

    @Schema(description = "状态: draft(草稿), published(已发布), scheduled(定时发布), offline(已下线，仅作者与管理员可见)")
    private String status;

    @Schema(description = "最后修改时间")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorVO {
        private Long id;
        private String username;
        private String avatar;

        @Schema(description = "作者职业，详情接口填充，列表接口不携带；为空时前端不展示该行")
        private String occupation;

        @Schema(description = "作者被关注数，仅详情接口填充")
        private Integer followers;

        @Schema(description = "当前浏览者是否已关注该作者，仅详情接口填充，匿名与作者本人恒为 false")
        private Boolean followed;

        @Schema(description = "该作者已发布文章数，仅详情接口填充")
        private Integer articleCount;

        @Schema(description = "该作者全部已发布文章的获赞总数，仅详情接口填充")
        private Integer likeCount;

        @Schema(description = "该作者全部已发布文章的阅读总量，仅详情接口填充")
        private Integer viewCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryVO {
        private Integer id;
        private String name;
    }
}
