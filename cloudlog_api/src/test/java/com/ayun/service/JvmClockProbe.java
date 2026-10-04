package com.ayun.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时区探针：把 JVM 默认时区钉成 UTC 再跑一段被测代码。
 * 本站的时刻口径是「库里存北京墙钟」（db-design.md §1「不做 UTC 存储」），而 JVM 默认时区由部署环境决定，
 * 本机在 +08 上两种写法算出同一个瞬间、永远测不出来（CI 的 runner 默认是 UTC）——只有把默认值显式改成 UTC，
 * 「代码问错了时钟」才会变成一次失败
 */
final class JvmClockProbe {

    private JvmClockProbe() {
    }

    /** 改的是 JVM 全局状态，finally 必须还原，否则同一 JVM 里后续用例跟着错位 */
    static void underUtc(Runnable action) {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            action.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * 断言 written 是北京墙钟：与同一线程上 UTC 基的 utcBasis 差整 8 小时（留 1 分钟取整余量）。
     * 基准刻意不取业务时区常量——否则实现退回裸 now() 也照样绿，等于没测
     */
    static void assertBeijingWallClock(String field, LocalDateTime written, LocalDateTime utcBasis) {
        long gapMinutes = Duration.between(utcBasis, written).toMinutes();
        assertTrue(gapMinutes >= 479 && gapMinutes <= 481,
                field + " 用的不是北京墙钟：与 UTC 基差 " + gapMinutes + " 分钟");
    }
}
