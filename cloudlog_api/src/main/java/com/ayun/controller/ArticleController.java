package com.ayun.controller;

import com.ayun.common.NotFoundException;
import com.ayun.common.Result;
import com.ayun.model.dto.PostCreateDTO;
import com.ayun.model.dto.PostQueryDTO;
import com.ayun.model.dto.PostUpdateDTO;
import com.ayun.model.vo.DraftPageVO;
import com.ayun.model.vo.LikeStateVO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostIdVO;
import com.ayun.model.vo.PostVO;
import com.ayun.service.ArticleService;
import com.ayun.service.FollowService;
import com.ayun.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
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

    @Autowired
    private LikeService likeService;

    @Autowired
    private FollowService followService;

    /**
     * 分页获取文章列表
     */
    @Operation(summary = "分页获取文章列表")
    @GetMapping
    public Result<PageResult<PostVO>> getArticles(@Valid PostQueryDTO queryDTO) {
        return Result.success(articleService.getArticles(queryDTO));
    }

    /**
     * 分页获取当前用户的未发布文章（草稿与定时发布），含分状态计数
     */
    @Operation(summary = "分页获取当前用户的未发布文章")
    @GetMapping("/drafts")
    public Result<DraftPageVO> getDrafts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(articleService.getDrafts(pageNum, pageSize, year, month, keyword, status));
    }

    /**
     * 分页获取当前用户「我的文章」（已发布 + 已下线，个人主页用）
     */
    @Operation(summary = "分页获取当前用户的文章（已发布与已下线）")
    @GetMapping("/mine")
    public Result<PageResult<PostVO>> getMyPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String keyword) {
        return Result.success(articleService.getMyPosts(page, pageSize, year, month, keyword));
    }

    /**
     * 获取热门文章
     */    @Operation(summary = "获取热门文章")
    @Parameter(name = "limit", description = "返回条数，最多 30 条；超出按 30 取", example = "10")
    @GetMapping("/hot")
    public Result<List<PostVO>> getHotArticles(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(articleService.getHotArticles(limit));
    }

    /**
     * 获取文章详情
     * 草稿与定时发布文章仅作者本人和管理员可见，其余情况统一返回 404
     */
    @Operation(summary = "获取文章详情")
    @Parameter(name = "id", description = "文章ID（雪花 ID）", example = "7123456789012345678")
    @GetMapping("/{id}")
    public Result<PostVO> getArticle(@PathVariable Long id) {
        PostVO article = articleService.getArticleById(id);
        if (article == null) {
            throw new NotFoundException("文章不存在或无权访问");
        }
        article.setLiked(likeService.viewerLikedPost(id));
        PostVO.AuthorVO author = article.getAuthor();
        if (author != null) {
            author.setFollowed(followService.viewerFollowedAuthor(author.getId()));
            author.setFollowers(followService.countFollowers(author.getId()));
        }
        return Result.success(article);
    }

    /**
     * 创建文章（仅限管理员）
     */
    @Operation(summary = "创建文章（仅限管理员）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PostIdVO> createPost(@Valid @RequestBody PostCreateDTO createDTO) {
        return Result.success(articleService.createPost(createDTO));
    }

    /**
     * 更新文章（仅限管理员）
     */
    @Operation(summary = "更新文章（仅限管理员）")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updatePost(@PathVariable Long id, @RequestBody PostUpdateDTO updateDTO) {
        articleService.updatePost(id, updateDTO);
        return Result.success();
    }

    /**
     * 删除文章（仅限管理员）
     */
    @Operation(summary = "删除文章（仅限管理员）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deletePost(@PathVariable Long id) {
        articleService.deletePost(id);
        return Result.success();
    }

    /**
     * 点赞文章（登录用户）。幂等：重复调用返回同一「已点赞」状态，不报错
     */
    @Operation(summary = "点赞文章（仅限登录用户）")
    @Parameter(name = "id", description = "文章ID（雪花 ID）", example = "7123456789012345678")
    @PostMapping("/{id}/like")
    public Result<LikeStateVO> likeArticle(@PathVariable Long id) {
        return Result.success(likeService.like(id));
    }

    /**
     * 取消点赞（登录用户）。幂等：未点赞过也返回「未点赞」状态，不报错
     */
    @Operation(summary = "取消点赞（仅限登录用户）")
    @Parameter(name = "id", description = "文章ID（雪花 ID）", example = "7123456789012345678")
    @DeleteMapping("/{id}/like")
    public Result<LikeStateVO> unlikeArticle(@PathVariable Long id) {
        return Result.success(likeService.unlike(id));
    }
}
