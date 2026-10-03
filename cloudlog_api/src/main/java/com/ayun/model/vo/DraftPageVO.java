package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 草稿箱分页结果。继承 PageResult 而不是换一层信封，前端读 data.list / data.total 的写法保持不变；
 * 多出的两个计数供页头的状态视图使用
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "草稿箱分页结果（含分状态计数）")
public class DraftPageVO extends PageResult<PostVO> {

    @Schema(description = "当前筛选范围内的草稿篇数")
    private Integer draftCount;

    @Schema(description = "当前筛选范围内的定时发布篇数")
    private Integer scheduledCount;
}
