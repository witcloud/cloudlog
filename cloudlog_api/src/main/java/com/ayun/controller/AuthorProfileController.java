package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.vo.AuthorProfileVO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostVO;
import com.ayun.model.vo.UserBriefVO;
import com.ayun.service.ArticleService;
import com.ayun.service.AuthorProfileService;
import com.ayun.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者主页控制器
 */
@Tag(name = "作者主页", description = "公开访问的作者身份、统计与关注意向，无需登录")
@RestController
@RequestMapping("/users")
public class AuthorProfileController {

    @Autowired
    private AuthorProfileService authorProfileService;

    @Autowired
    private FollowService followService;

    @Autowired
    private ArticleService articleService;

    /**
     * 作者主页公开视图。与 FollowController 同为 /users 前缀，方法与路径不重叠
     */
    @Operation(summary = "查询作者主页（公开，未登录时 followed 为 null）")
    @Parameter(name = "id", description = "作者用户ID", example = "1")
    @GetMapping("/{id}/profile")
    public Result<AuthorProfileVO> profile(@PathVariable Long id) {
        return Result.success(authorProfileService.getProfile(id));
    }

    /**
     * 作者主页「关注中」栏：TA 关注了哪些人。无需登录即可读，登录时行内带浏览者的关注状态
     */
    @Operation(summary = "查询该作者关注的用户列表（公开）")
    @Parameter(name = "id", description = "作者用户ID", example = "1")
    @GetMapping("/{id}/following")
    public Result<PageResult<UserBriefVO>> following(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(followService.listFollowingOf(id, page, pageSize));
    }

    /**
     * 作者主页「粉丝」栏：哪些人关注了 TA。无需登录即可读，登录时行内带浏览者的关注状态（回关入口）
     */
    @Operation(summary = "查询关注该作者的用户列表（公开）")
    @Parameter(name = "id", description = "作者用户ID", example = "1")
    @GetMapping("/{id}/followers")
    public Result<PageResult<UserBriefVO>> followers(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(followService.listFollowersOf(id, page, pageSize));
    }

    /**
     * 作者主页「点赞」栏：TA 点赞过的已发布文章，公开只读
     */
    @Operation(summary = "查询该作者点赞过的文章（公开）")
    @Parameter(name = "id", description = "作者用户ID", example = "1")
    @GetMapping("/{id}/liked-posts")
    public Result<PageResult<PostVO>> likedPosts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(articleService.getLikedPostsOf(id, page, pageSize));
    }
}
