package com.ayun.controller;

import com.ayun.common.BizException;
import com.ayun.common.Result;
import com.ayun.service.FileService;
import com.ayun.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final UserService userService;

    // 允许的图片格式
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");

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
        return fileService.upload(file, category);
    }
}
