package com.ayun.service;

import com.ayun.mapper.CategoryMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.TagMapper;
import com.ayun.model.vo.SiteStatsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 元数据服务：站点统计四项取自各自的查询，空站点的分类计数回落为空表而非 null；
 * 删除分类按全量口径（含草稿/定时）拦截仍有文章的分类
 */
@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private MetaService metaService;

    @Test
    @DisplayName("站点统计装配文章数、分类数、最近更新与分类计数")
    void shouldAssembleFourStats() {
        LocalDateTime latest = LocalDateTime.of(2026, 10, 1, 15, 17, 13);
        when(postMapper.countPublished()).thenReturn(6L);
        when(categoryMapper.countAll()).thenReturn(4L);
        when(postMapper.findLatestPublishedAt()).thenReturn(latest);
        when(postMapper.countPublishedByCategory())
                .thenReturn(List.of(categoryCount(1, 3), categoryCount(2, 2)));

        SiteStatsVO stats = metaService.getSiteStats();

        assertEquals(6L, stats.getArticleCount().longValue());
        assertEquals(4L, stats.getCategoryCount().longValue());
        assertEquals(latest, stats.getLatestPublishedAt());
        assertEquals(3, stats.getCategoryCounts().get(1).intValue());
        assertEquals(2, stats.getCategoryCounts().get(2).intValue());
    }

    @Test
    @DisplayName("空站点：最近更新为 null，分类计数为空表")
    void shouldFallBackToNullLatestAndEmptyCountsOnEmptySite() {
        when(postMapper.countPublished()).thenReturn(0L);
        when(categoryMapper.countAll()).thenReturn(0L);
        when(postMapper.findLatestPublishedAt()).thenReturn(null);
        when(postMapper.countPublishedByCategory()).thenReturn(Collections.emptyList());

        SiteStatsVO stats = metaService.getSiteStats();

        assertNull(stats.getLatestPublishedAt());
        assertTrue(stats.getCategoryCounts().isEmpty());
    }

    @Test
    @DisplayName("删除分类：分类下仍有文章（含草稿与定时）时拒绝，且不落删除语句")
    void shouldRefuseDeleteWhileCategoryStillHoldsPosts() {
        when(categoryMapper.countPosts(3)).thenReturn(3);

        RuntimeException error = assertThrows(RuntimeException.class, () -> metaService.deleteCategory(3));

        assertTrue(error.getMessage().contains("3 篇文章"), error.getMessage());
        verify(categoryMapper, never()).deleteById(3);
    }

    @Test
    @DisplayName("删除分类：无文章的分类照常删除")
    void shouldDeleteEmptyCategory() {
        when(categoryMapper.countPosts(4)).thenReturn(0);

        metaService.deleteCategory(4);

        verify(categoryMapper).deleteById(4);
    }

    private SiteStatsVO.CategoryCount categoryCount(Integer categoryId, Integer count) {
        SiteStatsVO.CategoryCount row = new SiteStatsVO.CategoryCount();
        row.setCategoryId(categoryId);
        row.setCount(count);
        return row;
    }
}
