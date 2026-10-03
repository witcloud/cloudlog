package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.entity.Category;
import com.ayun.entity.Tag;
import com.ayun.model.vo.SiteStatsVO;
import com.ayun.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 元数据管理控制器（分类与标签）
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "元数据管理", description = "文章分类与标签的增删改查")
@RestController
@RequestMapping
public class MetaController {

    @Autowired
    private MetaService metaService;

    /**
     * 获取所有分类
     */
    @Operation(summary = "获取所有分类", description = "公开接口，文章数口径仅已发布文章")
    @GetMapping("/categories")
    public Result<List<Category>> getCategories() {
        return Result.success(metaService.getAllCategories());
    }

    /**
     * 获取站点统计
     */
    @Operation(summary = "获取站点统计", description = "首页统计卡与分类计数，口径为已发布文章")
    @GetMapping("/site/stats")
    public Result<SiteStatsVO> getSiteStats() {
        return Result.success(metaService.getSiteStats());
    }

    /**
     * 获取所有标签
     */
    @Operation(summary = "获取所有标签")
    @GetMapping("/tags")
    public Result<List<Tag>> getTags() {
        return Result.success(metaService.getAllTags());
    }

    /**
     * 获取所有分类（含全量文章数，仅限管理员）
     */
    @Operation(summary = "获取所有分类（含文章数，仅限管理员）",
            description = "文章数口径为全量（含草稿、定时与已下线）；公开 GET /categories 的口径仅已发布")
    @GetMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Category>> getAdminCategories() {
        return Result.success(metaService.getAllCategoriesWithCount());
    }

    /**
     * 创建分类（仅限管理员）
     */
    @Operation(summary = "创建分类（仅限管理员）")
    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> createCategory(@RequestBody Category category) {
        metaService.createCategory(category);
        return Result.success();
    }

    /**
     * 更新分类（仅限管理员）
     */
    @Operation(summary = "更新分类（仅限管理员）")
    @Parameter(name = "id", description = "分类ID", example = "1")
    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateCategory(@PathVariable Integer id, @RequestBody Category category) {
        category.setId(id);
        metaService.updateCategory(category);
        return Result.success();
    }

    /**
     * 删除分类（仅限管理员）
     */
    @Operation(summary = "删除分类（仅限管理员）",
            description = "分类下仍有文章（含草稿与定时）时拒绝删除（400），并回写文章数")
    @Parameter(name = "id", description = "分类ID", example = "1")
    @DeleteMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteCategory(@PathVariable Integer id) {
        metaService.deleteCategory(id);
        return Result.success();
    }

    /**
     * 创建标签（仅限管理员）
     */
    @Operation(summary = "创建标签（仅限管理员）")
    @PostMapping("/admin/tags")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> createTag(@RequestBody com.ayun.entity.Tag tag) {
        metaService.createTag(tag);
        return Result.success();
    }

    /**
     * 获取所有标签（含关联文章数，仅限管理员）
     */
    @Operation(summary = "获取所有标签（含文章数，仅限管理员）")
    @GetMapping("/admin/tags")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<com.ayun.model.vo.AdminTagVO>> getAdminTags() {
        return Result.success(metaService.getAllTagsWithCount());
    }

    /**
     * 修改标签名称（仅限管理员）
     */
    @Operation(summary = "修改标签名称（仅限管理员）")
    @Parameter(name = "id", description = "标签ID", example = "1")
    @PutMapping("/admin/tags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateTag(@PathVariable Integer id, @RequestBody com.ayun.entity.Tag tag) {
        metaService.updateTag(id, tag.getName());
        return Result.success();
    }

    /**
     * 合并标签（仅限管理员）：把源标签的文章关联全部转移到目标标签后删除源标签
     */
    @Operation(summary = "合并标签（仅限管理员）")
    @PostMapping("/admin/tags/merge")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> mergeTags(@RequestParam Integer sourceId, @RequestParam Integer targetId) {
        metaService.mergeTags(sourceId, targetId);
        return Result.success();
    }

    /**
     * 删除标签（仅限管理员）
     */
    @Operation(summary = "删除标签（仅限管理员）")
    @Parameter(name = "id", description = "标签ID", example = "1")
    @DeleteMapping("/admin/tags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteTag(@PathVariable Integer id) {
        metaService.deleteTag(id);
        return Result.success();
    }
}
