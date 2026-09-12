package com.ayun.service;

import com.ayun.dto.LoginRequest;
import com.ayun.dto.RegisterRequest;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证业务逻辑类
 */
@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private VerificationCodeService verificationCodeService;

    /**
     * 用户注册
     * @param request 注册请求参数
     */
    public void register(RegisterRequest request) {
        // 校验验证码
        if (!verificationCodeService.verifyCode(request.getEmail(), request.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("该邮箱已被注册");
        }
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new RuntimeException("该用户名已被占用");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("user")
                .status(1)
                .build();
        userMapper.insert(user);
    }

    /**
     * 用户登录
     * @param request 登录请求参数
     * @return 包含 Token 和用户信息的 Map
     */
    public Map<String, Object> login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userMapper.findByEmail(request.getEmail());
        String token = jwtUtils.generateToken(user.getEmail());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("role", user.getRole());
        result.put("user", userInfo);

        return result;
    }

    /**
     * 根据邮箱获取当前登录用户信息
     * @param email 用户邮箱
     * @return 用户对象
     */
    public User getCurrentUser(String email) {
        return userMapper.findByEmail(email);
    }

    /**
     * 发送注册验证码
     * @param email 用户邮箱
     */
    public void sendRegistrationCode(String email) {
        if (userMapper.findByEmail(email) != null) {
            throw new RuntimeException("该邮箱已被注册");
        }
        verificationCodeService.sendRegistrationCode(email);
    }
}
