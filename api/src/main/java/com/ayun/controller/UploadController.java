package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 文件上传控制器
 */
@Tag(name = "文件上传", description = "文件上传接口")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FileService fileService;
    private final UserMapper userMapper;

    // 允许的图片格式
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * 上传用户头像
     */
    @Operation(summary = "上传用户头像")
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        // 1. 文件校验
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_EXTENSIONS.contains(contentType.toLowerCase())) {
            return Result.error(400, "不支持的文件格式，请上传图片 (jpg, png, gif, webp)");
        }

        // 2. 调用服务层上传文件
        String avatarUrl = fileService.upload(file);

        // 3. 获取当前登录用户并更新头像
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByEmail(email);
        
        if (user != null) {
            user.setAvatar(avatarUrl);
            userMapper.update(user);
        } else {
            return Result.error(404, "未找到当前用户信息");
        }

        // 4. 返回成功结果
        return Result.success(avatarUrl);
    }
}
