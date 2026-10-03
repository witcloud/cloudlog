package com.ayun.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关于页文案（站点级文本，后台可编辑；库中未配置或已清空时回落内置默认值）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "关于页文案")
public class AboutContentVO {

    @Size(max = 50, message = "作者名长度不能超过 50 个字符")
    @Schema(description = "关于页主标题（作者名）", example = "Ayun")
    private String name;

    @Size(max = 50, message = "身份说明长度不能超过 50 个字符")
    @Schema(description = "作者身份说明", example = "CloudLog 博主")
    private String role;

    @Size(max = 200, message = "导语长度不能超过 200 个字符")
    @Schema(description = "关于页导语")
    private String lead;

    @Size(max = 255, message = "头像地址长度不能超过 255 个字符")
    @Schema(description = "头像访问地址（由头像上传接口返回）；留空则前端回落兜底头像")
    private String avatar;
}
