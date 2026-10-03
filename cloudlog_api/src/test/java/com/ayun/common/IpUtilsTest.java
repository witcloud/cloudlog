package com.ayun.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 来源地址取值口径：登录防爆破与阅读量去重共用它，两处必须解析出同一个 IP。
 * X-Forwarded-For 是否采信由部署形态决定，两条分支各自都要钉住——采信与否直接决定爆破计数键是不是攻击者可控的
 */
class IpUtilsTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String clientIp(boolean trustForwardedFor) {
        return new IpUtils(trustForwardedFor).clientIp();
    }

    @Test
    @DisplayName("缺省（直连部署）不采信 X-Forwarded-For，只取直连地址")
    void ignoresForwardedForByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7");
        request.setRemoteAddr("10.0.0.9");
        bind(request);

        // 头是客户端自己写的，采信就等于让它挑一个计数键
        assertEquals("10.0.0.9", clientIp(false));
    }

    @Test
    @DisplayName("可信代理之后才采信，且多级代理只取首值")
    void takesOnlyTheFirstForwardedForValueWhenTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 192.168.0.2");
        request.setRemoteAddr("127.0.0.1");
        bind(request);

        assertEquals("203.0.113.7", clientIp(true));
    }

    @Test
    @DisplayName("采信时首值两侧空白被去掉，键位不会因空格分裂成两个")
    void trimsSurroundingSpaces() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "  203.0.113.8 ");
        bind(request);

        assertEquals("203.0.113.8", clientIp(true));
    }

    @Test
    @DisplayName("采信但请求根本没带代理头时回落直连地址")
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        bind(request);

        assertEquals("10.0.0.9", clientIp(true));
    }
}
