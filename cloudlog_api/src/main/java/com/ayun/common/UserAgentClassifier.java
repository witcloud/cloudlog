package com.ayun.common;

/**
 * User-Agent 归类：后台日志分析只出七类粗标签，不做完整设备解析。
 * 纯小写子串包含规则，不引入 UA 解析库（business-model.md §5.7 第 4 条）。
 * 判定顺序即优先级，是这套规则的一部分，改动任何一条都要连带核对它下面的分支
 */
public final class UserAgentClassifier {

    public static final String CHROME = "Chrome";

    public static final String SAFARI = "Safari";

    public static final String FIREFOX = "Firefox";

    public static final String EDGE = "Edge";

    public static final String MOBILE = "Mobile";

    public static final String BOT = "Bot";

    public static final String OTHER = "其他";

    /**
     * 爬虫与脚本客户端的标识词。写入侧用它拒绝入库，读取侧归类时它排在最前
     */
    private static final String[] BOT_TOKENS = {
            "bot", "spider", "crawler", "curl", "wget", "python",
            "okhttp", "scrapy", "java/", "httpclient", "headless", "lighthouse",
    };

    /**
     * 移动设备标识词。排在四个浏览器名之前判定，理由见 {@link #classify(String)}
     */
    private static final String[] MOBILE_TOKENS = {
            "mobile", "android", "iphone", "ipad", "ipod", "windows phone",
    };

    private UserAgentClassifier() {
    }

    /**
     * 归为七类之一。顺序是 Bot → Mobile → Edge → Firefox → Chrome → Safari → 其他，两个顺序约束各有代价：
     * <ul>
     *   <li>Edge / Chrome / Safari 的 UA 串里都嵌着下游那一个的名字（Edge 带 {@code Chrome/…Safari/…}，
     *       Chrome 带 {@code Safari/537.36}），所以必须从最特殊的往判，反过来的话 Edge 与 Chrome 全都会落到 Safari。</li>
     *   <li>Mobile 抢在浏览器名之前，是因为这一栏只有一个标签位：Android 自带浏览器的串里同时有
     *       {@code Mobile} 与 {@code Safari}，若浏览器优先，「Mobile」这一类基本收不到行，
     *       博主想看「读者有多少人在手机上」就没人了。代价是 Chrome / Safari / Firefox 四类在此口径下
     *       只代表桌面引擎，不代表设备。</li>
     * </ul>
     * 已知不精确处：iPadOS 13+ 的 Safari 申报的是 Macintosh 串，不解析版本号就分不出来，按本方法的设计归入 Safari
     * @param userAgent 原始 UA，可为空
     * @return 七类标签之一，永不返回 null
     */
    public static String classify(String userAgent) {
        if (isBot(userAgent)) {
            return BOT;
        }
        String lower = userAgent.toLowerCase();
        if (containsAny(lower, MOBILE_TOKENS)) {
            return MOBILE;
        }
        if (lower.contains("edg")) {
            return EDGE;
        }
        if (lower.contains("firefox") || lower.contains("fxios")) {
            return FIREFOX;
        }
        if (lower.contains("chrome") || lower.contains("crios") || lower.contains("chromium")) {
            return CHROME;
        }
        if (lower.contains("safari")) {
            return SAFARI;
        }
        return OTHER;
    }

    /**
     * 是否爬虫或脚本客户端：命中任一标识词即算，**空 UA 也算**——真实浏览器不会不申报 UA，
     * 空串几乎只来自 curl 一类的裸请求，而它同样无法归类
     * @param userAgent 原始 UA，可为空
     * @return 是否应当按 bot 处置
     */
    public static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        return containsAny(userAgent.toLowerCase(), BOT_TOKENS);
    }

    private static boolean containsAny(String lower, String[] tokens) {
        for (String token : tokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
