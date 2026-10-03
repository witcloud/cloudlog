package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页返回对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果对象")
public class PageResult<T> {

    @Schema(description = "总条数")
    private Long total;

    @Schema(description = "当前页码")
    private Integer page;

    @Schema(description = "每页条数")
    private Integer pageSize;

    @Schema(description = "数据列表")
    private List<T> list;

    public static <T> PageResult<T> of(Long total, Integer page, Integer pageSize, List<T> list) {
        return new PageResult<>(total, page, pageSize, list);
    }
}
