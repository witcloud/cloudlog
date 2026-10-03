package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章创建结果视图对象
 * 只回传新建文章 ID，供编辑器回填后切换到更新路径
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文章创建结果")
public class PostIdVO {

    @Schema(description = "新建文章ID（雪花 ID）", example = "7123456789012345678")
    private Long id;
}
