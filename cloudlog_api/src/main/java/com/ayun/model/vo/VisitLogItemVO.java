package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 访问日志明细的一行。与 {@link VisitRow} 的差别只有一处：这一类里没有能装下原始地址的字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "访问日志明细行")
public class VisitLogItemVO {

    @Schema(description = "记录 ID", example = "1024")
    private Integer id;

    @Schema(description = "访问时刻", example = "2026-10-03 14:20:31")
    private LocalDateTime createdAt;

    @Schema(description = "文章 ID（雪花 ID，字符串下发）", example = "7123456789012345678")
    private Long postId;

    @Schema(description = "文章标题；文章已删时为 null，前端按「已删除文章」呈现且不给跳转")
    private String postTitle;

    @Schema(description = "脱敏后的来源地址，如 112.10.*.*", example = "112.10.*.*")
    private String ipMasked;

    @Schema(description = "UA 归类标签：Chrome / Safari / Firefox / Edge / Mobile / Bot / 其他", example = "Chrome")
    private String userAgentType;

    @Schema(description = "原始 UA，供悬浮查看完整串", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) …")
    private String userAgent;

    /**
     * 明细查询的原始行，带未脱敏的地址，只供服务层脱敏与归类之用，不出现在任何响应里
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisitRow {

        private Integer id;

        private LocalDateTime createdAt;

        private Long postId;

        /** 文章标题，文章已删时为 null */
        private String postTitle;

        /** 原始来源地址，不下发 */
        private String ip;

        private String userAgent;
    }
}
