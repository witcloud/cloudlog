package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关注操作后的权威状态，前端按钮以此为准
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "关注状态")
public class FollowStateVO {

    @Schema(description = "当前用户是否已关注该作者")
    private boolean followed;

    @Schema(description = "该作者的被关注数")
    private Integer followers;
}
