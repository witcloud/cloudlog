package com.ayun.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 来源地址的两处口径：展示侧的脱敏与检索侧的前缀补位必须同源，
 * 否则博主照着屏幕上那串去搜会搜不到自己刚看到的那一行
 */
class IpMaskUtilsTest {

    @Test
    @DisplayName("IPv4 保留前两段，后两段一律以 * 代替")
    void masksIpv4HostPart() {
        assertEquals("112.10.*.*", IpMaskUtils.mask("112.10.20.30"));
        assertEquals("203.0.*.*", IpMaskUtils.mask("203.0.113.7"));
    }

    @Test
    @DisplayName("IPv6 同构：保留前两组，其余折成一枚 *")
    void masksIpv6HostPart() {
        assertEquals("2001:db8:*", IpMaskUtils.mask("2001:db8:1::5"));
        assertEquals("0:0:*", IpMaskUtils.mask("0:0:0:0:0:0:0:1"));
    }

    @Test
    @DisplayName("分不出两段的输入整串打码，空值也不留白")
    void masksUnparsableInputEntirely() {
        assertEquals("*", IpMaskUtils.mask(null));
        assertEquals("*", IpMaskUtils.mask("  "));
        assertEquals("*", IpMaskUtils.mask("localhost"));
    }

    @Test
    @DisplayName("前缀补上分隔符再接通配：112.1 不该连 112.100.x.x 一起命中")
    void prefixPatternAnchorsOnTheSeparator() {
        assertEquals("112.10.%", IpMaskUtils.likePattern("112.10"));
        assertEquals("112.%", IpMaskUtils.likePattern("112"));
        assertEquals("2001:db8:%", IpMaskUtils.likePattern("2001:db8"));
    }

    @Test
    @DisplayName("照抄屏幕上那串带 * 的展示值也认，且不会出现两个点")
    void acceptsMaskedValueAsPrefix() {
        assertEquals("112.10.%", IpMaskUtils.likePattern("112.10.*.*"));
        assertEquals("112.10.%", IpMaskUtils.likePattern("112.10."));
    }

    @Test
    @DisplayName("LIKE 的两个通配符按字面量处理，输入两端空白被去掉")
    void escapesWildcardsAndTrims() {
        assertEquals("1.2\\_3.%", IpMaskUtils.likePattern("1.2_3"));
        assertEquals("100\\%.%", IpMaskUtils.likePattern(" 100% "));
        assertEquals("112.10.%", IpMaskUtils.likePattern("  112.10  "));
    }

    @Test
    @DisplayName("空前缀回 null，由调用方按「不筛」处理")
    void blankPrefixMeansNoFilter() {
        assertNull(IpMaskUtils.likePattern(null));
        assertNull(IpMaskUtils.likePattern("   "));
    }
}
