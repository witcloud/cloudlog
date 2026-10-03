package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理后台标签视图对象（带关联文章数）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理后台标签视图对象")
public class AdminTagVO {

    @Schema(description = "标签ID")
    private Integer id;

    @Schema(description = "标签名称", example = "Redis")
    private String name;

    @Schema(description = "关联文章数量")
    private Integer articleCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
