package com.ayun.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户信息")
public class User {
    
    @Schema(description = "用户ID", example = "1")
    private Integer id;
    
    @Schema(description = "电子邮箱", example = "admin@ayun.com")
    private String email;
    
    @JsonIgnore
    @Schema(description = "密码（返回时隐藏）")
    private String password;
    
    @Schema(description = "用户名", example = "ayun")
    private String username;
    
    @Schema(description = "头像 URL")
    private String avatar;
    
    @Schema(description = "个人简介")
    private String bio;
    
    @Schema(description = "用户角色", example = "admin", allowableValues = {"admin", "user"})
    private String role; 
    
    @Schema(description = "用户状态", example = "1", allowableValues = {"0", "1"})
    private Integer status; 
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
