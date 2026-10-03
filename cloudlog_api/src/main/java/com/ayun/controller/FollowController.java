package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.model.vo.FollowStateVO;
import com.ayun.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户关注控制器
 */
@Tag(name = "用户关注", description = "读者对作者的关注与取消关注")
@RestController
@RequestMapping("/users")
public class FollowController {

    @Autowired
    private FollowService followService;

    /**
     * 关注作者（登录用户）。幂等：重复调用返回同一「已关注」状态，不报错
     */
    @Operation(summary = "关注作者（仅限登录用户）")
    @Parameter(name = "id", description = "被关注用户ID", example = "1")
    @PostMapping("/{id}/follow")
    public Result<FollowStateVO> follow(@PathVariable Long id) {
        return Result.success(followService.follow(id));
    }

    /**
     * 取消关注（登录用户）。幂等：未关注过也返回「未关注」状态，不报错
     */
    @Operation(summary = "取消关注（仅限登录用户）")
    @Parameter(name = "id", description = "被关注用户ID", example = "1")
    @DeleteMapping("/{id}/follow")
    public Result<FollowStateVO> unfollow(@PathVariable Long id) {
        return Result.success(followService.unfollow(id));
    }
}
