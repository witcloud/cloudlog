package com.ayun.service;

import com.ayun.entity.Post;
import com.ayun.mapper.PostMapper;
import com.ayun.model.dto.AdminArticleQueryDTO;
import com.ayun.model.vo.AdminArticleVO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理后台文章业务逻辑类
 */
@Service
public class AdminArticleService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private ArticleService articleService;

    /**
     * 分页多条件查询全站文章（含草稿/定时发布）
     */
    public PageResult<AdminArticleVO> getArticles(AdminArticleQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<Post> list = postMapper.findAdminAll(
                queryDTO.getStatus(),
                queryDTO.getAuthorId(),
                queryDTO.getCategoryId(),
                queryDTO.getKeyword(),
                queryDTO.getStartDate(),
                queryDTO.getEndDate()
        );
        PageInfo<Post> pageInfo = new PageInfo<>(list);
        return PageResult.of(
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                AdminArticleVO.fromList(list)
        );
    }

    /**
     * 发表过文章的作者列表（筛选项）
     */
    public List<PostVO.AuthorVO> getAuthors() {
        return postMapper.findAuthors();
    }

    /**
     * 删除文章：与前台共用 ArticleService 的同一条级联实现，后台不再自带一份（business-model.md §5.7 第 7 条）
     */
    public void deleteArticle(Long id) {
        articleService.deletePost(id);
    }
}
