package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未发布内容的分状态计数，取不到算 0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "草稿与定时发布的篇数")
public class StatusCountVO {

    @Schema(description = "草稿篇数")
    private Integer draftCount;

    @Schema(description = "定时发布篇数")
    private Integer scheduledCount;
}
