package com.ayun.dto;

/**
 * 用户名字符规则的单一来源。注册与改资料过去各写一份，注册侧限「中文/字母/数字/_/-」且 2-30 位、
 * 改资料侧 2-20 位且不带白名单，同一个字段两套口径；两处都引这里，改一处即两处生效
 */
public final class UsernameRules {

    /** 允许中文、字母、数字、下划线与中划线；长度上下限写在 DTO 的 @Size 上 */
    public static final String PATTERN = "^[\\u4e00-\\u9fa5a-zA-Z0-9_-]+$";

    /** 拒绝提示语，与 @Pattern 的 message 同源 */
    public static final String MESSAGE = "用户名仅支持中文、字母、数字、下划线和中划线";

    private UsernameRules() {
    }
}
