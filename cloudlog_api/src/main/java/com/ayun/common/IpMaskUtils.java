package com.ayun.common;

import java.util.regex.Pattern;

/**
 * 来源地址脱敏：后台分析只展示与下发网段，不展示完整地址。
 * 与 {@link IpUtils#clientIp()} 配对使用——写入侧存原始值，读取侧一律过这里，
 * 任何响应体都不得出现未脱敏的地址；按前缀检索原始值时也以此处的分段口径为准
 */
public final class IpMaskUtils {

    private static final String MASK = "*";

    private static final char IPV4_SEPARATOR = '.';

    private static final char IPV6_SEPARATOR = ':';

    private IpMaskUtils() {
    }

    /**
     * 保留前两段、其余以 {@code *} 代替。两族的补位方式不同，各按各的段结构来：
     * <ul>
     *   <li>IPv4 定长四段，逐段等宽打码（{@code 112.10.20.30 → 112.10.*.*}），屏幕上看到的位数与真实地址对得上；</li>
     *   <li>IPv6 有 {@code ::} 压缩，段数本身不可信，折成一枚尾随的 {@code *}（{@code 2001:db8:1::5 → 2001:db8:*}）。</li>
     * </ul>
     * 分不出两段的输入（畸形值、单段值）整串打码——宁可少给，不可漏给
     * @param address 原始来源地址，可为空
     * @return 脱敏后的地址
     */
    public static String mask(String address) {
        if (address == null || address.isBlank()) {
            return MASK;
        }
        boolean ipv6 = address.indexOf(IPV6_SEPARATOR) >= 0;
        char separator = ipv6 ? IPV6_SEPARATOR : IPV4_SEPARATOR;
        String[] parts = address.split(Pattern.quote(String.valueOf(separator)), -1);
        if (parts.length < 3) {
            return MASK;
        }
        StringBuilder masked = new StringBuilder(parts[0]).append(separator).append(parts[1]);
        int remaining = ipv6 ? 1 : parts.length - 2;
        for (int i = 0; i < remaining; i++) {
            masked.append(separator).append(MASK);
        }
        return masked.toString();
    }

    /**
     * 把用户输入的脱敏前缀换成可匹配原始值的 LIKE 模式：{@code 112.10 → 112.10.%}。
     * 补上那个分隔符是硬保证——少了它 {@code 112.1} 会连 {@code 112.100.x.x} 一起命中。
     * 照抄屏幕上那串展示值也认：{@code *} 段与尾随的空段到此为止，不再往下拼（否则补位会拼出两个点）。
     * 转义在这里做而不在 XML 做：XML 那份 REPLACE 会把本方法刚加上的 {@code %} 一并转义掉
     * @param prefix 用户输入的脱敏前缀
     * @return 用于原始列前缀匹配的模式值；前缀为空时返回 null，由调用方按「不筛」处理
     */
    public static String likePattern(String prefix) {
        if (prefix == null) {
            return null;
        }
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        char separator = trimmed.indexOf(IPV6_SEPARATOR) >= 0 ? IPV6_SEPARATOR : IPV4_SEPARATOR;
        StringBuilder kept = new StringBuilder();
        for (String segment : trimmed.split(Pattern.quote(String.valueOf(separator)), -1)) {
            if (segment.isEmpty() || MASK.equals(segment)) {
                break;
            }
            if (kept.length() > 0) {
                kept.append(separator);
            }
            kept.append(escape(segment));
        }
        if (kept.length() == 0) {
            return "%";
        }
        return kept + String.valueOf(separator) + "%";
    }

    /**
     * 摘掉 LIKE 的两个通配符与转义符本身，让前缀按字面量匹配
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
