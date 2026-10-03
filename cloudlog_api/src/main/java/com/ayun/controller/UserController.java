package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.dto.EmailChangeRequest;
import com.ayun.dto.PasswordChangeRequest;
import com.ayun.dto.UserProfileUpdateDTO;
import com.ayun.entity.User;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostVO;
import com.ayun.model.vo.ProfileStatsVO;
import com.ayun.model.vo.UserBriefVO;
import com.ayun.service.ArticleService;
import com.ayun.service.FollowService;
import com.ayun.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理", description = "用户个人资料管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated // @RequestParam 上的约束靠方法级校验生效
public class UserController {

    private final UserService userService;
    private final FollowService followService;
    private final ArticleService articleService;

    /**
     * 获取当前登录用户信息
     */
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/profile")
    public Result<User> getProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByEmail(email);
        if (user != null) {
            // 隐藏密码
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /**
     * 更新用户个人资料
     */
    @Operation(summary = "更新用户个人资料")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UserProfileUpdateDTO profileUpdateDTO) {
        // 1. 获取当前登录用户的 Email
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        
        // 2. 调用 Service 层处理业务逻辑
        userService.updateProfile(email, profileUpdateDTO);

        return Result.success();
    }

    /**
     * 向待绑定的新邮箱发送修改验证码
     */
    @Operation(summary = "发送修改邮箱验证码")
    @PostMapping("/email/code")
    public Result<Void> sendEmailChangeCode(@Parameter(description = "新邮箱")
                                            @RequestParam @Email(message = "新邮箱格式不正确") String newEmail) {
        userService.sendEmailChangeCode(newEmail);
        return Result.success();
    }

    /**
     * 修改登录邮箱：口令 + 新邮箱验证码 + 唯一性通过后改绑
     */
    @Operation(summary = "修改登录邮箱")
    @PutMapping("/email")
    public Result<Void> changeEmail(@Valid @RequestBody EmailChangeRequest emailChangeRequest) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.changeEmail(email, emailChangeRequest);
        return Result.success();
    }

    /**
     * 修改登录密码：校验当前口令后换用新口令，此前签发的 token 全部作废
     */
    @Operation(summary = "修改登录密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest passwordChangeRequest) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.changePassword(email, passwordChangeRequest);
        return Result.success();
    }

    /**
     * 个人主页统计行的两个计数
     */
    @Operation(summary = "当前用户的关注与粉丝计数")
    @GetMapping("/profile/stats")
    public Result<ProfileStatsVO> getProfileStats() {
        return Result.success(followService.profileStats());
    }

    /**
     * 我关注的用户列表
     */
    @Operation(summary = "我关注的用户列表")
    @GetMapping("/following")
    public Result<PageResult<UserBriefVO>> getFollowing(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.success(followService.listFollowing(page, pageSize));
    }

    /**
     * 关注我的用户列表
     */
    @Operation(summary = "关注我的用户列表")
    @GetMapping("/followers")
    public Result<PageResult<UserBriefVO>> getFollowers(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.success(followService.listFollowers(page, pageSize));
    }

    /**
     * 我点赞过的文章列表
     */
    @Operation(summary = "我点赞过的文章列表")
    @GetMapping("/liked-posts")
    public Result<PageResult<PostVO>> getLikedPosts(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.success(articleService.getLikedPosts(page, pageSize));
    }
}
