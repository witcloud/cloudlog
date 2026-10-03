package com.ayun.controller;

import com.ayun.common.BizException;
import com.ayun.common.Result;
import com.ayun.service.FileService;
import com.ayun.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传控制器
 */
@Tag(name = "文件上传", description = "文件上传接口")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FileService fileService;
    private final UserService userService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 允许的图片格式
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");

    /** 每用户每日上传次数上限：单文件虽有 10MB 上限，但次数不限等于登录用户可循环上传占满对象存储 */
    private static final long DAILY_UPLOAD_LIMIT = 100;

    private static final String UPLOAD_DAILY_KEY_PREFIX = "upload:daily:";

    /**
     * 上传用户头像
     */
    @Operation(summary = "上传用户头像", description = "登录用户即可：改自己的头像是普通用户的合法需求，不收管理员门槛")
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String avatarUrl = uploadImage(file, "avatar");

        // 头像地址绑定到当前登录用户：身份只取安全上下文，写库归 UserService
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.updateAvatar(email, avatarUrl);

        return Result.success(avatarUrl);
    }

    /**
     * 上传站点级图片（关于页头像等素材）：只转存对象，不绑定到任何用户
     */
    @Operation(summary = "上传站点级图片", description = "仅转存对象并返回访问地址，不改动用户资料。仅管理员可用")
    @PostMapping("/site-image")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> uploadSiteImage(@RequestParam("file") MultipartFile file) {
        return Result.success(uploadImage(file, "site/avatar"));
    }

    /**
     * 上传文章正文配图（Vditor 拖拽/粘贴/工具栏插入）
     */
    @Operation(summary = "上传文章正文配图", description = "仅管理员可用：与创建文章的授权口径一致，能发文的人才占得到配图的对象存储额度")
    @PostMapping("/article-image")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> uploadArticleImage(@RequestParam("file") MultipartFile file) {
        return Result.success(uploadImage(file, "post/content"));
    }

    /**
     * 上传文章封面图
     */
    @Operation(summary = "上传文章封面图", description = "仅管理员可用：与创建文章的授权口径一致")
    @PostMapping("/cover-image")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> uploadCoverImage(@RequestParam("file") MultipartFile file) {
        return Result.success(uploadImage(file, "post/cover"));
    }

    /**
     * 图片通用校验与转存：这里的 Content-Type 只是第一道快速过滤（挡掉明显不是图片的请求），
     * 真正的裁决在 FileService 的魔数比对。校验后交给 FileService 存 MinIO，返回完整访问地址。
     * 大小上限由 spring.servlet.multipart.max-file-size 统一约束，超限由全局异常处理转 400。
     */
    private String uploadImage(MultipartFile file, String category) {
        if (file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_EXTENSIONS.contains(contentType.toLowerCase())) {
            throw new BizException("不支持的文件格式，请上传图片 (jpg, png, gif, webp)");
        }
        requireDailyQuota();
        return fileService.upload(file, category);
    }

    /**
     * 每用户每日上传次数额度。格式与体积校验之后、转存对象之前计数：
     * 被限流的请求不占 MinIO 存储，校验失败的请求也不烧额度
     */
    private void requireDailyQuota() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        com.ayun.entity.User user = userService.findByEmail(email);
        if (user == null) {
            return;
        }
        String key = UPLOAD_DAILY_KEY_PREFIX + user.getId();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(1).toMillis(), TimeUnit.MILLISECONDS);
        }
        if (count != null && count > DAILY_UPLOAD_LIMIT) {
            throw new BizException("今日上传次数已达上限，请明日再试");
        }
    }
}
