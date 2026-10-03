package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 站点统计视图对象（前台首页统计卡与分类计数）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "站点统计")
public class SiteStatsVO {

    @Schema(description = "已发布文章总数")
    private Long articleCount;

    @Schema(description = "分类总数")
    private Long categoryCount;

    @Schema(description = "最近更新时间，无已发布文章时为 null")
    private LocalDateTime latestPublishedAt;

    @Schema(description = "各分类下的已发布文章数，键为分类 ID")
    private Map<Integer, Integer> categoryCounts;

    /**
     * 分类计数查询行（GROUP BY 的结果，装配后转为 categoryCounts）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryCount {

        @Schema(description = "分类 ID")
        private Integer categoryId;

        @Schema(description = "该分类下已发布文章数")
        private Integer count;
    }
}
