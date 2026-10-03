package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.entity.Comment;
import com.ayun.service.CommentService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 获取文章评论列表
     */
    @Operation(summary = "获取文章评论列表")
    @Parameter(name = "id", description = "文章ID（雪花 ID）", example = "7123456789012345678")
    @GetMapping("/articles/{id}/comments")
    public Result<List<Comment>> getComments(@PathVariable Long id) {
        return Result.success(commentService.getCommentsByPostId(id));
    }

    /**
     * 发表评论。评论人身份与审核状态一律由服务端决定，controller 不做任何身份回填；
     * data 返回该评论的最终 status（approved/pending），前端据此区分「已发布」与「待审核」
     */
    @Operation(summary = "发表评论", description = "data 为该评论的最终审核状态：approved(已发布) / pending(待审核)")
    @PostMapping("/comments")
    public Result<String> addComment(@RequestBody Comment comment) {
        return Result.success(commentService.addComment(comment));
    }
}
