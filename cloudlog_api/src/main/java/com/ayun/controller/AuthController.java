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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * 登出。JWT 本身无状态，服务端把该账号的失效水位推到此刻作废全部在存 token；
     * 幂等设计：未带 token 或上下文未认证时同样返回成功，前端清完本地凭据即可
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // /auth/** 整体 permitAll，未登录时这里是 AnonymousAuthenticationToken，须排除
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            authService.logout(auth.getName());
        }
        return Result.success();
    }
}
