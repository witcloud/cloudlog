package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 草稿管理控制器
 */
@Tag(name = "草稿管理", description = "草稿的删除与清空")
@RestController
@RequestMapping("/drafts")
public class DraftController {

    @Autowired
    private ArticleService articleService;

    /**
     * 删除单篇草稿
     */
    @Operation(summary = "删除单篇草稿")
    @Parameter(name = "id", description = "文章ID（雪花 ID）", example = "7123456789012345678")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDraft(@PathVariable Long id) {
        articleService.deleteDraft(id);
        return Result.success();
    }

    /**
     * 清空当前用户的草稿箱
     */
    @Operation(summary = "清空当前用户的草稿箱")
    @DeleteMapping
    public Result<Void> clearDrafts() {
        articleService.clearDrafts();
        return Result.success();
    }
}
