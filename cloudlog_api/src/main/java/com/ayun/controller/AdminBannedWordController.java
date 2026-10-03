package com.ayun.controller;

import com.ayun.common.Result;
import com.ayun.entity.BannedWord;
import com.ayun.model.dto.AdminBannedWordQueryDTO;
import com.ayun.model.dto.BannedWordBatchImportDTO;
import com.ayun.model.vo.BannedWordImportResultVO;
import com.ayun.model.vo.PageResult;
import com.ayun.service.BannedWordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台违禁词库。词库属内容数据，入口在「评论管理」而不在「系统设置」（ui-design.md §3.11）
 */
@Tag(name = "后台-违禁词库", description = "评论违禁词的增删启停与批量导入")
@RestController
@RequestMapping("/admin/banned-words")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBannedWordController {

    @Autowired
    private BannedWordService bannedWordService;

    @Operation(summary = "分页查询违禁词库", description = "keyword 按词面模糊匹配；按添加时间倒序")
    @GetMapping
    public Result<PageResult<BannedWord>> getWords(AdminBannedWordQueryDTO queryDTO) {
        return Result.success(bannedWordService.getWords(queryDTO));
    }

    @Operation(summary = "添加违禁词",
            description = "word 经 trim 后入库；词库已有该词时返回 400 与中文原因。action 留空按默认 pending")
    @PostMapping
    public Result<Void> addWord(@RequestBody BannedWord bannedWord) {
        bannedWordService.addWord(bannedWord.getWord(), bannedWord.getAction());
        return Result.success();
    }

    @Operation(summary = "批量导入违禁词",
            description = "text 为原始文本，服务端按换行与中英文逗号拆分、trim、去空去重后入库。"
                    + "返回 added（真正入库数）与 skipped（文本内重复 + 词库已有 + 超过 50 字符，三者合并计数不区分原因）")
    @PostMapping("/batch-import")
    public Result<BannedWordImportResultVO> batchImport(@RequestBody BannedWordBatchImportDTO importDTO) {
        return Result.success(bannedWordService.batchImport(importDTO.getText(), importDTO.getAction()));
    }

    @Operation(summary = "启用/停用违禁词", description = "停用不是删除：词条保留，机审跳过它，可随时恢复")
    @Parameter(name = "id", description = "违禁词ID", example = "1")
    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Integer id) {
        bannedWordService.toggle(id);
        return Result.success();
    }

    @Operation(summary = "删除违禁词", description = "硬删词条；不回写存量评论已记录的命中词")
    @Parameter(name = "id", description = "违禁词ID", example = "1")
    @DeleteMapping("/{id}")
    public Result<Void> deleteWord(@PathVariable Integer id) {
        bannedWordService.deleteWord(id);
        return Result.success();
    }
}
