package com.ayun.security;

import com.ayun.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置类
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 配置 JWT 认证过滤器
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    /**
     * 配置安全过滤链
     *
     * @param apiDocsEnabled 接口文档是否开放。默认（dev）为真、保持联调便利；prod 由 `application-prod.yml` 关为假，
     *                       此时三条文档路径不再 permitAll，落到「其余一律要求登录」（business-model.md §3.3「运维面收敛」）
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper,
                                           @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // 禁用 CSRF
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态会话
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/auth/**").permitAll() // 认证接口开放
                    .requestMatchers("/error").permitAll() // 错误转发路径，否则 404/500 会被兜底成 403
                    // 爬虫直读的三份产物按精确路径放行：查询只取已发布文章，不含未发布数据；
                    // 落到兜底的「其余一律要求登录」就等于对搜索引擎与订阅器隐身
                    .requestMatchers("/robots.txt", "/sitemap.xml", "/feed.xml").permitAll()
                    // 本人的草稿列表与未发布文章列表仅登录可见，必须排在下面 /articles/** 公开规则之前
                    // （落到通配上就等于公开可读，属 §5.5「关键陷阱」那条）
                    .requestMatchers(HttpMethod.GET, "/articles/drafts", "/articles/mine").authenticated()
                    .requestMatchers(HttpMethod.GET, "/articles/**", "/categories/**", "/tags/**").permitAll() // 文章相关 GET 请求开放
                    .requestMatchers(HttpMethod.GET, "/site/**").permitAll(); // 站点级文案（关于页）公开读取
                // 作者主页四栏（身份 / 关注中 / 粉丝 / 点赞）公开可读，但 /users/** 其余路径
                // （POST|DELETE /users/{id}/follow）仍落到兜底的 authenticated。规则只逐条放开这几个 GET
                auth.requestMatchers(HttpMethod.GET,
                        "/users/*/profile", "/users/*/following", "/users/*/followers", "/users/*/liked-posts").permitAll();
                if (apiDocsEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll(); // 接口文档开放（仅 dev）
                }
                // 监控端点只放开健康检查，其余端点需登录；prod 另有 exposure.include=health 收窄可见面
                auth.requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN") // 管理后台接口仅管理员可用
                    .anyRequest().authenticated(); // 其他请求均需认证
            })
            // 无状态链默认用 Http403ForbiddenEntryPoint 兜底，未登录也会被回成 403；
            // 前端只有拿到 401 才能区分「会话失效」和「权限不足」，前者才该清掉本地 token
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeError(response, objectMapper, HttpStatus.FORBIDDEN, "无权限执行此操作"))
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 安全过滤链内的异常发生在 MVC 之前，走不到 GlobalExceptionHandler，需自行写出 Result 结构
     */
    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Result.error(status.value(), message));
    }

    /**
     * 配置密码编码器（使用 BCrypt）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 获取认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
