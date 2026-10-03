package com.ayun.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 文章查询参数 DTO
 */
@Data
@Schema(description = "文章查询参数")
public class PostQueryDTO {

    // 这是公开端点上的分页入参，不给上下限就等于让一次 ?pageSize=100000 把整张已发布表装进响应；
    // 50 这一档取后台列表最大页容量（前台各页最大用到 20）
    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    @Schema(description = "每页条数，最大 50", defaultValue = "10")
    private Integer pageSize = 10;

    @Schema(description = "分类ID")
    private Integer categoryId;

    @Schema(description = "作者用户ID，供作者主页只列该作者的已发布文章")
    private Long authorId;

    @Schema(description = "标签ID")
    private Integer tagId;

    @Schema(description = "搜索关键词")
    private String keyword;

    @Schema(description = "排序方式，仅三个合法值：latest（置顶优先，再按置顶权重与发布时间倒序，默认）、recommended（首页「推荐」tab 用：置顶优先，其余按加权热度分倒序）、popular（侧栏热榜 /articles/hot 用：纯加权热度倒序，阅读 + 点赞×3 + 评论×5，再按发布天数做引力衰减，置顶不参与）；其余取值按 latest 处理",
            allowableValues = {"latest", "popular", "recommended"}, defaultValue = "latest")
    private String sort = "latest";
}
