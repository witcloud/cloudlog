package com.ayun.service;

import com.ayun.mapper.PostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * 后台删除不再自带一份级联实现，必须委托 ArticleService 的唯一实现
 * （business-model.md §3.2「文章删除路径一致性」、§5.7 第 7 条；级联顺序本身由 ArticleServiceTest 覆盖）
 */
@ExtendWith(MockitoExtension.class)
class AdminArticleServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private AdminArticleService adminArticleService;

    @Test
    @DisplayName("后台删除文章：委托 ArticleService.deletePost，前台后台行为一致")
    void deleteArticleDelegatesToDeletePost() {
        adminArticleService.deleteArticle(1L);

        verify(articleService).deletePost(1L);
    }
}
