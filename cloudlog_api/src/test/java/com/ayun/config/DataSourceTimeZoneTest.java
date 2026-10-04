package com.ayun.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连接级时区口径。时刻列都是 {@code TIMESTAMP}：MySQL 存绝对瞬时、按**会话时区**换算读写，
 * {@code NOW()} 与 {@code CURRENT_TIMESTAMP} 报的也是会话时区的墙钟。会话只要跟着数据库主机的
 * {@code SYSTEM} 走，「服务端 GMT+8」这条声明就押在主机配置上——列默认值与 {@code ON UPDATE}
 * 生成的 {@code created_at} / {@code updated_at} 随之漂，而服务层按北京墙钟写入的 {@code published_at}
 * 不漂，同表就成了两套基（db-design.md §1）。
 * 只能对真库跑：要测的是驱动连上去之后会话**实际**是什么，URL 里写了参数不等于生效。
 * 钉的是偏移量而不是 'Asia/Shanghai'：本机 MySQL 未导入时区表，命名时区报 1298，
 * 用名字会让每一条连接都失败——所以这里断言的正是 '+08:00' 这个字面值
 */
@SpringBootTest
class DataSourceTimeZoneTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("每条连接的会话时区被驱动显式钉成 +08:00，而不是跟着数据库主机漂的 SYSTEM")
    void sessionTimeZoneIsPinnedToBusinessOffset() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT @@session.time_zone, NOW() = CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00')")) {

            assertTrue(rows.next(), "该回一行");
            assertEquals("+08:00", rows.getString(1),
                    "会话时区须是显式偏移量；SYSTEM 意味着它由数据库主机决定，"
                            + "于是 NOW() / CURRENT_TIMESTAMP 写的列与服务层写的列不同基");
            assertEquals(1, rows.getInt(2), "钉住之后 NOW() 与显式 +08 基准逐刻相等，SQL 侧不必再包装 CONVERT_TZ");
        }
    }
}
