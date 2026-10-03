package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论违禁词实体（词库一词一行，business-model.md §2.9）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评论违禁词")
public class BannedWord {

    /** 命中后转人工待审，不做终局裁决 */
    public static final String ACTION_PENDING = "pending";

    /** 命中后直接拒绝，评论不入库 */
    public static final String ACTION_REJECT = "reject";

    @Schema(description = "违禁词ID", example = "1")
    private Integer id;

    @Schema(description = "违禁词，存 trim 后的原样大小写；机审匹配时两边转小写做子串包含", example = "广告")
    private String word;

    @Schema(description = "命中处置动作: pending(转人工待审), reject(直接拒绝)", example = "pending")
    private String action;

    @Schema(description = "是否启用: 1-参与匹配, 0-停用（保留词条，机审跳过）", example = "1")
    private Integer isEnabled;

    @Schema(description = "添加时间")
    private LocalDateTime createdAt;

    @Schema(description = "最后改动时间（启停、改动作）")
    private LocalDateTime updatedAt;
}
