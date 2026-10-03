package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.dto.AdminUserQueryDTO;
import com.ayun.model.vo.AdminUserVO;
import com.ayun.model.vo.PageResult;
import com.ayun.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台用户管理
 */
@Tag(name = "后台-用户管理", description = "管理员对注册用户的查询与禁用/启用")
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @Operation(summary = "分页查询注册用户")
    @GetMapping
    public Result<PageResult<AdminUserVO>> getUsers(@Valid AdminUserQueryDTO queryDTO) {
        return Result.success(adminUserService.getUsers(queryDTO));
    }

    @Operation(summary = "禁用/启用用户", description = "status 取 1(正常) 或 0(禁用)，管理员账号不可禁用")
    @Parameter(name = "id", description = "用户ID", example = "2")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.updateStatus(id, status);
        return Result.success();
    }
}
