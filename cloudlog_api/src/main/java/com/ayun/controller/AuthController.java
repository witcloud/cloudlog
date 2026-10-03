package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.dto.LoginRequest;
import com.ayun.dto.PasswordResetRequest;
import com.ayun.dto.RegisterRequest;
import com.ayun.dto.SendCodeRequest;
import com.ayun.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证管理控制器
 */
@Tag(name = "认证管理", description = "用户注册、登录与密码找回")
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
     * 发送注册验证码。用 POST 而不是 GET：发一封真邮件、占一次该邮箱的频控额度，都是有副作用的动作，
     * 挂在 GET 上就会被浏览器预取、爬虫与代理重试重复触发
     */
    @Operation(summary = "发送注册验证码")
    @PostMapping("/code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendRegistrationCode(request.getEmail());
        return Result.success();
    }

    /**
     * 发送重置密码验证码。与注册发码同为一个「往这个邮箱发一枚码」的动作，共用同一个请求体与同一套格式校验
     */
    @Operation(summary = "发送重置密码验证码")
    @PostMapping("/password/code")
    public Result<Void> sendPasswordResetCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendPasswordResetCode(request.getEmail());
        return Result.success();
    }

    /**
     * 凭验证码重置密码
     */
    @Operation(summary = "重置密码")
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }
}
