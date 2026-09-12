package com.ayun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置类，用于生成 Swagger 接口文档
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 信息
     * @return OpenAPI 对象
     */
    @Bean
    public OpenAPI cloudLogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CloudLog 接口文档")
                        .description("基于 Spring Boot 3 构建的 CloudLog 系统 RESTful 接口文档")
                        .version("v1.1")
                        .contact(new Contact().name("Ayun").email("contact@ayun.com"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()));
    }

    /**
     * 配置安全方案（JWT 认证）
     * @return SecurityScheme 对象
     */
    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }
}
