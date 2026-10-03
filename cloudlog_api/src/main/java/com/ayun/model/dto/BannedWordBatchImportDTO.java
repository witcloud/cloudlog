package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 违禁词批量导入入参。text 是**未拆分的原始文本**，拆分规则由服务端承担，
 * 客户端不做预处理，以免两侧各写一套拆法
 */
@Data
@Schema(description = "违禁词批量导入入参")
public class BannedWordBatchImportDTO {

    @Schema(description = "原始文本，按换行与中英文逗号拆分", example = "广告\n代购，刷单\n\n广告")
    private String text;

    @Schema(description = "本批词的处置动作: pending / reject；留空按默认 pending", example = "pending")
    private String action;
}
