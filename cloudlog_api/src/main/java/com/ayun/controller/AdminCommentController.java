package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.dto.AdminCommentQueryDTO;
import com.ayun.model.vo.AdminCommentVO;
import com.ayun.model.vo.PageResult;
import com.ayun.service.AdminCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台评论审核
 */
@Tag(name = "后台-评论管理", description = "管理员对全站评论的审核与删除")
@RestController
@RequestMapping("/admin/comments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    @Autowired
    private AdminCommentService adminCommentService;

    @Operation(summary = "分页查询全站评论")
    @GetMapping
    public Result<PageResult<AdminCommentVO>> getComments(@Valid AdminCommentQueryDTO queryDTO) {
        return Result.success(adminCommentService.getComments(queryDTO));
    }

    @Operation(summary = "获取各审核状态的评论数")
    @GetMapping("/status-counts")
    public Result<Map<String, Long>> getStatusCounts() {
        return Result.success(adminCommentService.getStatusCounts());
    }

    @Operation(summary = "审核评论", description = "status 可取 pending(退回待审)/approved(通过)/rejected(拒绝)")
    @Parameter(name = "id", description = "评论ID", example = "1")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestParam String status) {
        adminCommentService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除评论", description = "联动删除其直接回复")
    @Parameter(name = "id", description = "评论ID", example = "1")
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable Integer id) {
        adminCommentService.deleteComment(id);
        return Result.success();
    }
}
