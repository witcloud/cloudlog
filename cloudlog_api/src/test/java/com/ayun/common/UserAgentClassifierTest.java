package com.ayun.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UA 七类归类的分支顺序是这套规则的全部难度所在：真实 UA 串里层层嵌套彼此的名字，
 * 每个用例都同时是「这一条判对了」与「它没有被上面那一条抢走」的断言
 */
class UserAgentClassifierTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String EDGE_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";

    private static final String SAFARI_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";

    private static final String FIREFOX_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0";

    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final String SAFARI_IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    @Test
    @DisplayName("Edge 的串里带着 Chrome 与 Safari，必须最先被 Edge 认领")
    void edgeWinsOverItsOwnEmbeddedNames() {
        assertEquals(UserAgentClassifier.EDGE, UserAgentClassifier.classify(EDGE_WINDOWS));
    }

    @Test
    @DisplayName("Chrome 的串里带着 Safari，轮到 Chrome 时先于 Safari")
    void chromeWinsOverSafariItEmbeds() {
        assertEquals(UserAgentClassifier.CHROME, UserAgentClassifier.classify(CHROME_WINDOWS));
    }

    @Test
    @DisplayName("Safari 自己不含 Chrome，落到了它该落的那一格")
    void safariIsTheRemainderAfterChrome() {
        assertEquals(UserAgentClassifier.SAFARI, UserAgentClassifier.classify(SAFARI_MAC));
    }

    @Test
    @DisplayName("Firefox")
    void classifiesFirefox() {
        assertEquals(UserAgentClassifier.FIREFOX, UserAgentClassifier.classify(FIREFOX_WINDOWS));
    }

    @Test
    @DisplayName("手机上的 Chrome 与 Safari 都归 Mobile——这一栏只有一个标签位，设备优先")
    void mobileTakesTheSlotAheadOfBrowserNames() {
        assertEquals(UserAgentClassifier.MOBILE, UserAgentClassifier.classify(CHROME_ANDROID));
        assertEquals(UserAgentClassifier.MOBILE, UserAgentClassifier.classify(SAFARI_IPHONE));
    }

    @Test
    @DisplayName("爬虫与脚本客户端一律 Bot，大小写不论")
    void classifiesCrawlersAndTooling() {
        assertEquals(UserAgentClassifier.BOT, UserAgentClassifier.classify(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        assertEquals(UserAgentClassifier.BOT, UserAgentClassifier.classify(
                "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)"));
        assertEquals(UserAgentClassifier.BOT, UserAgentClassifier.classify("curl/8.4.0"));
        assertEquals(UserAgentClassifier.BOT, UserAgentClassifier.classify("python-requests/2.31.0"));
        assertEquals(UserAgentClassifier.BOT, UserAgentClassifier.classify(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36"));
    }

    @Test
    @DisplayName("分不进六类的归「其他」")
    void fallsBackToOther() {
        assertEquals(UserAgentClassifier.OTHER, UserAgentClassifier.classify("Lynx/2.8.9rel.1 libwww-FM/2.14"));
    }

    @Test
    @DisplayName("空 UA 按 Bot 处置：真实浏览器不会不申报，空串几乎只来自裸请求")
    void treatsBlankUserAgentAsBot() {
        assertTrue(UserAgentClassifier.isBot(null));
        assertTrue(UserAgentClassifier.isBot("   "));
        assertTrue(UserAgentClassifier.isBot("Scrapy/2.11 (+https://scrapy.org)"));
    }

    @Test
    @DisplayName("四个主流桌面浏览器都不该被 bot 名单误伤")
    void doesNotMistakeBrowsersForBots() {
        assertFalse(UserAgentClassifier.isBot(CHROME_WINDOWS));
        assertFalse(UserAgentClassifier.isBot(EDGE_WINDOWS));
        assertFalse(UserAgentClassifier.isBot(SAFARI_MAC));
        assertFalse(UserAgentClassifier.isBot(FIREFOX_WINDOWS));
    }
}
