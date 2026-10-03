package com.ayun.service;

import com.ayun.mapper.PostPublishMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 定时发布调度单元测试
 *
 * 到期判定、幂等与不回退这三条语义由 SQL 谓词承担（db-design.md §5.7「幂等性由 SQL 谓词本身保证」），
 * 因此这里解析随包发布的 PostPublishMapper.xml 得到真实 SQL 来断言，而不是复刻一份；
 * MySQL 上的实际执行结果不属单元测试范围，另以探针 SQL 交付。
 */
@ExtendWith(MockitoExtension.class)
class ScheduledPublishServiceTest {

    @Mock
    private PostPublishMapper postPublishMapper;

    @InjectMocks
    private ScheduledPublishService scheduledPublishService;

    /** 取真正打进 classpath 的那条 SQL，空白归一化便于断言 */
    private static String publishDueSql() {
        Configuration configuration = new Configuration();
        try (InputStream in = ScheduledPublishServiceTest.class.getResourceAsStream("/mapper/PostPublishMapper.xml")) {
            assertNotNull(in, "未找到 classpath 下的 mapper/PostPublishMapper.xml");
            new XMLMapperBuilder(in, configuration, "mapper/PostPublishMapper.xml", configuration.getSqlFragments()).parse();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return configuration.getMappedStatement("com.ayun.mapper.PostPublishMapper.publishDueArticles")
                .getBoundSql(null).getSql().replaceAll("\\s+", " ").trim();
    }

    private static String setClause(String sql) {
        return sql.substring(sql.indexOf("SET ") + 4, sql.indexOf(" WHERE"));
    }

    private static String whereClause(String sql) {
        return sql.substring(sql.indexOf(" WHERE"));
    }

    // ---- S1：到期自动发布，且不得改写 published_at ----

    @Test
    @DisplayName("到期发布：只把 status 翻成 published，不写 published_at、不写 scheduled")
    void dueScanFlipsStatusOnly() {
        String sql = publishDueSql();

        assertTrue(sql.startsWith("UPDATE posts SET status = 'published'"), "应为批量条件更新：" + sql);
        assertFalse(setClause(sql).contains("published_at"),
                "published_at 存的就是计划时刻（首次对外可见的时刻），调度器不得改写它");
        assertFalse(setClause(sql).contains("scheduled"), "SET 侧不得出现 scheduled，否则可能回退状态");
    }

    // ---- S2：未到期不发布 ----

    @Test
    @DisplayName("未到期不发布：谓词要求计划时刻已到")
    void scanOnlyMatchesRowsWhosePlannedTimeHasPassed() {
        String where = whereClause(publishDueSql());

        assertTrue(where.contains("published_at <= NOW()"), "谓词需按计划时刻过滤：" + where);
    }

    @Test
    @DisplayName("published_at 为空的存量 scheduled 行不由调度器兜底")
    void scanLeavesScheduledRowsWithoutPlannedTimeAlone() {
        String where = whereClause(publishDueSql());

        assertFalse(where.contains("IS NULL"), "空计划时刻行按 §5.3 交人工处理");
        assertFalse(where.contains("COALESCE"), "不得用兜底默认值把空计划时刻当成到期");
    }

    // ---- S3：重复扫描幂等 ----

    @Test
    @DisplayName("幂等：谓词锁 status='scheduled'，第二次扫描匹配集为空")
    void repeatedScanMatchesNoRows() {
        String where = whereClause(publishDueSql());

        assertTrue(where.contains("status = 'scheduled'"),
                "谓词必须锁住 scheduled：翻转后该行即脱离匹配集，重复执行影响 0 行");
    }

    @Test
    @DisplayName("幂等：连续两次扫描各自只发一条条件更新，无补偿写")
    void twoConsecutiveScansDoNotAccumulateWrites() {
        when(postPublishMapper.publishDueArticles()).thenReturn(1).thenReturn(0);

        assertEquals(1, scheduledPublishService.publishDueArticles());
        assertEquals(0, scheduledPublishService.publishDueArticles());

        verify(postPublishMapper, times(2)).publishDueArticles();
        verifyNoMoreInteractions(postPublishMapper);
    }

    // ---- S4：调度与管理员手工立即发布的竞态 ----

    @Test
    @DisplayName("竞态：手工发布已提交时调度影响 0 行，且不读后写回")
    void manualPublishWinsWithoutBeingRolledBack() {
        // 管理员已把该行改为 published：条件 UPDATE 的谓词不再命中，返回 0
        when(postPublishMapper.publishDueArticles()).thenReturn(0);

        assertEquals(0, scheduledPublishService.publishDueArticles());

        // 服务不得「读出行再逐条写回」——那才会把已发布的行覆盖回 scheduled
        verifyNoMoreInteractions(postPublishMapper);
    }

    @Test
    @DisplayName("竞态：调度器不经过通用 update 语句写文章")
    void scanNeverGoesThroughTheGenericUpdateStatement() {
        when(postPublishMapper.publishDueArticles()).thenReturn(3);

        assertEquals(3, scheduledPublishService.publishDueArticles());

        verify(postPublishMapper).publishDueArticles();
        verifyNoMoreInteractions(postPublishMapper);
    }
}
