package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.dto.LoginRequest;
import com.ayun.dto.RegisterRequest;
import com.ayun.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证管理控制器
 */
@Tag(name = "认证管理", description = "用户注册与登录认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 发送注册验证码
     */
    @Operation(summary = "发送注册验证码")
    @GetMapping("/code")
    public Result<Void> sendCode(@Parameter(description = "用户邮箱") @RequestParam String email) {
        authService.sendRegistrationCode(email);
        return Result.success();
    }
}
