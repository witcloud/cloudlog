package com.ayun.service;

import com.ayun.entity.Post;
import com.ayun.mapper.PostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 存量摘要回填：这条是「摘要改为服务端派生」之后对存量数据的补票，跑在启动期，
 * 所以它必须做到重复执行无副作用、且一轮失败不能把应用带倒
 */
@ExtendWith(MockitoExtension.class)
class ExcerptBackfillRunnerTest {

    @Mock
    private PostMapper postMapper;

    private ExcerptBackfillRunner runner(boolean enabled) {
        return new ExcerptBackfillRunner(postMapper, enabled);
    }

    @Test
    @DisplayName("命中行逐篇派生并只回写摘要一列")
    void fillsEveryPendingRow() {
        when(postMapper.findNeedingExcerpt()).thenReturn(List.of(
                Post.builder().id(3L).content("## 标题\n\n正文的第一句话").build(),
                Post.builder().id(4L).content("另一篇的开头").build()));

        runner(true).run();

        verify(postMapper).updateExcerpt(3L, "标题 正文的第一句话");
        verify(postMapper).updateExcerpt(4L, "另一篇的开头");
    }

    @Test
    @DisplayName("派生结果为空即跳过写入，否则该行下轮启动仍落回待处理集合")
    void skipsRowsWithNoReadableText() {
        when(postMapper.findNeedingExcerpt())
                .thenReturn(List.of(Post.builder().id(5L).content("```java\n只有代码\n```").build()));

        runner(true).run();

        verify(postMapper, never()).updateExcerpt(anyLong(), any());
    }

    @Test
    @DisplayName("开关置 false 时整轮跳过，连扫描都不发")
    void disabledSkipsTheWholeScan() {
        runner(false).run();

        verifyNoInteractions(postMapper);
    }

    @Test
    @DisplayName("库不可用也不把应用带倒：回填失败只记日志")
    void failureNeverBlocksStartup() {
        when(postMapper.findNeedingExcerpt()).thenThrow(new IllegalStateException("数据源不可用"));

        assertDoesNotThrow(() -> runner(true).run());
        verify(postMapper, never()).updateExcerpt(anyLong(), any());
    }
}
