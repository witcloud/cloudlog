package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录日志明细的一行。与 {@link LoginRow} 的差别只有一处：这一类里没有能装下原始地址的字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录日志明细行")
public class LoginLogVO {

    @Schema(description = "记录 ID", example = "1024")
    private Integer id;

    @Schema(description = "尝试时刻", example = "2026-10-03 14:20:31")
    private LocalDateTime createdAt;

    @Schema(description = "尝试时输入的邮箱，原样记录：账号不存在也照样有一行", example = "reader@ayun.com")
    private String email;

    @Schema(description = "登录成功时的用户 ID（雪花 ID，字符串下发）；三种失败都为 null")
    private Long userId;

    @Schema(description = "脱敏后的来源地址，如 203.0.*.*", example = "203.0.*.*")
    private String ipMasked;

    @Schema(description = "UA 归类标签：Chrome / Safari / Firefox / Edge / Mobile / Bot / 其他", example = "Bot")
    private String userAgentType;

    @Schema(description = "原始 UA，供悬浮查看完整串", example = "curl/8.4.0")
    private String userAgent;

    @Schema(description = "结果: success(成功) / wrong_password(口令错) / locked(锁定期内的尝试) / disabled(账号禁用等其他认证失败)",
            example = "wrong_password")
    private String result;

    /**
     * 明细查询的原始行，带未脱敏的地址，只供服务层脱敏与归类之用，不出现在任何响应里
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRow {

        private Integer id;

        private LocalDateTime createdAt;

        private String email;

        /** 仅登录成功时非空 */
        private Long userId;

        /** 原始来源地址，不下发 */
        private String ip;

        private String userAgent;

        private String result;
    }
}
