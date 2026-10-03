package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 个人主页统计行的两个计数，取不到就返回 0，不用估算值兜底
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "个人主页关注计数")
public class ProfileStatsVO {

    @Schema(description = "我关注的用户数")
    private Integer followingCount;

    @Schema(description = "关注我的用户数")
    private Integer followersCount;
}
