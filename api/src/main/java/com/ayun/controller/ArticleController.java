package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.dto.PostCreateDTO;
import com.ayun.model.dto.PostQueryDTO;
import com.ayun.model.dto.PostUpdateDTO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostVO;
import com.ayun.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 文章管理控制器
 */
@Tag(name = "文章管理", description = "文章的增删改查及点赞功能")
@RestController
@RequestMapping("/articles")
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    /**
     * 分页获取文章列表
     */
    @Operation(summary = "分页获取文章列表")
    @GetMapping
    public Result<PageResult<PostVO>> getArticles(PostQueryDTO queryDTO) {
        return Result.success(articleService.getArticles(queryDTO));
    }

    /**
     * 获取文章详情
     */
    @Operation(summary = "获取文章详情")
    @Parameter(name = "id", description = "文章ID", example = "1")
    @GetMapping("/{id}")
    public Result<PostVO> getArticle(@PathVariable Integer id) {
        return Result.success(articleService.getArticleById(id));
    }

    /**
     * 创建文章（仅限管理员）
     */
    @Operation(summary = "创建文章（仅限管理员）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> createPost(@Valid @RequestBody PostCreateDTO createDTO) {
        articleService.createPost(createDTO);
        return Result.success();
    }

    /**
     * 更新文章（仅限管理员）
     */
    @Operation(summary = "更新文章（仅限管理员）")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updatePost(@PathVariable Integer id, @RequestBody PostUpdateDTO updateDTO) {
        articleService.updatePost(id, updateDTO);
        return Result.success();
    }

    /**
     * 删除文章（仅限管理员）
     */
    @Operation(summary = "删除文章（仅限管理员）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deletePost(@PathVariable Integer id) {
        articleService.deletePost(id);
        return Result.success();
    }

    /**
     * 文章点赞
     */
    @Operation(summary = "文章点赞")
    @PostMapping("/{id}/like")
    public Result<Void> likePost(@PathVariable Integer id) {
        articleService.likePost(id);
        return Result.success();
    }
}
