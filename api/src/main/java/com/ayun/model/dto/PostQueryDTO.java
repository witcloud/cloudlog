package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文章查询参数 DTO
 */
@Data
@Schema(description = "文章查询参数")
public class PostQueryDTO {

    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", defaultValue = "10")
    private Integer pageSize = 10;

    @Schema(description = "分类ID")
    private Integer categoryId;

    @Schema(description = "标签ID")
    private Integer tagId;

    @Schema(description = "搜索关键词")
    private String keyword;

    @Schema(description = "排序方式: latest(最新), popular(热门)")
    private String sort = "latest";
}
