package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.entity.Comment;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.service.CommentService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论管理控制器
 */
@Tag(name = "评论管理", description = "文章评论的获取与发布")
@RestController
@RequestMapping
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 获取文章评论列表
     */
    @Operation(summary = "获取文章评论列表")
    @Parameter(name = "id", description = "文章ID", example = "1")
    @GetMapping("/articles/{id}/comments")
    public Result<List<Comment>> getComments(@PathVariable Integer id) {
        return Result.success(commentService.getCommentsByPostId(id));
    }

    /**
     * 发表评论
     */
    @Operation(summary = "发表评论")
    @PostMapping("/comments")
    public Result<Void> addComment(@RequestBody Comment comment) {
        String authName = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!authName.equals("anonymousUser")) {
            User user = userMapper.findByEmail(authName);
            comment.setUserId(user.getId());
        }
        commentService.addComment(comment);
        return Result.success();
    }
}
