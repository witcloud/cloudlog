package com.ayun.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站点配置实体类（键值对存储）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "站点配置")
public class SiteSetting {

    @Schema(description = "配置键", example = "comment_moderation")
    private String settingKey;

    @Schema(description = "配置值", example = "true")
    private String settingValue;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
