package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞操作后的权威状态，前端按钮以此为准
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "点赞状态")
public class LikeStateVO {

    @Schema(description = "当前用户是否已点赞")
    private boolean liked;

    @Schema(description = "点赞数")
    private Integer likeCount;
}
