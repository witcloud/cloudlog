package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.dto.UserProfileUpdateDTO;
import com.ayun.entity.User;
import com.ayun.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理", description = "用户个人资料管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
}
