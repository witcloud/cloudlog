package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录尝试日志实体（business-model.md §2.12）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录尝试日志")
public class LoginLog {

    /** 登录成功，同时回填 user_id */
    public static final String RESULT_SUCCESS = "success";

    /** 口令校验失败；邮箱未注册走的是同一条 BadCredentials 出口，不区分二者 */
    public static final String RESULT_WRONG_PASSWORD = "wrong_password";

    /** 锁定期内的尝试：不再校验口令，直接拒 */
    public static final String RESULT_LOCKED = "locked";

    /** 账号禁用等其他认证失败：不计入爆破计数 */
    public static final String RESULT_DISABLED = "disabled";

    @Schema(description = "记录ID", example = "1")
    private Integer id;

    @Schema(description = "尝试登录时输入的邮箱，原样记录，账号不存在也照样留一条", example = "reader@ayun.com")
    private String email;

    @Schema(description = "登录成功时回填的用户ID（用户雪花 ID），失败为 NULL", example = "7123456789012345678")
    private Long userId;

    @Schema(description = "来源IP原始值，对外只出脱敏值，本字段不进任何响应体", example = "203.0.113.9")
    private String ip;

    @Schema(description = "浏览器 User-Agent 原文")
    private String userAgent;

    @Schema(description = "结果: success(成功), wrong_password(口令错), locked(锁定期内), disabled(账号禁用等其他认证失败)",
            example = "wrong_password")
    private String result;

    @Schema(description = "尝试时刻")
    private LocalDateTime createdAt;
}
