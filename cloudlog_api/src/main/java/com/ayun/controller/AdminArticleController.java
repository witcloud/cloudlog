package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.dto.AdminArticleQueryDTO;
import com.ayun.model.vo.AdminArticleVO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostVO;
import com.ayun.service.AdminArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台文章管理
 */
@Tag(name = "后台-文章管理", description = "管理员对全站文章的查询与删除")
@RestController
@RequestMapping("/admin/articles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminArticleController {

    @Autowired
    private AdminArticleService adminArticleService;

    @Operation(summary = "分页查询全站文章（含草稿/定时发布）")
    @GetMapping
    public Result<PageResult<AdminArticleVO>> getArticles(@Valid AdminArticleQueryDTO queryDTO) {
        return Result.success(adminArticleService.getArticles(queryDTO));
    }

    @Operation(summary = "获取文章作者列表（筛选项）")
    @GetMapping("/authors")
    public Result<List<PostVO.AuthorVO>> getAuthors() {
        return Result.success(adminArticleService.getAuthors());
    }

    @Operation(summary = "删除文章", description = "联动清理文章的标签关联与评论")
    @Parameter(name = "id", description = "文章ID（雪花 ID）", example = "7123456789012345678")
    @DeleteMapping("/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        adminArticleService.deleteArticle(id);
        return Result.success();
    }
}
