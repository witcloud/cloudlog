package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 违禁词批量导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "违禁词批量导入结果")
public class BannedWordImportResultVO {

    @Schema(description = "真正入库的词数", example = "3")
    private Integer added;

    @Schema(description = "未入库的词数：文本内重复、词库已有、空项与超长项合并计数，不区分原因", example = "2")
    private Integer skipped;
}
