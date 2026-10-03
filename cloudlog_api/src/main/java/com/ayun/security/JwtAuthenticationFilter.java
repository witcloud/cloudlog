package com.ayun.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器，拦截请求并校验 Token
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    @Lazy
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Value("${jwt.header}")
    private String tokenHeader;

    @Value("${jwt.prefix}")
    private String tokenPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader(tokenHeader);
        String email = null;
        String jwt = null;

        // 检查请求头中是否包含 Token
        if (authHeader != null && authHeader.startsWith(tokenPrefix)) {
            jwt = authHeader.substring(tokenPrefix.length());
            try {
                email = jwtUtils.extractEmail(jwt);
            } catch (Exception e) {
                // 过期或伪造的 Token 是常态流量（有效期 7 天、无刷新令牌），处置结果只是保持未认证，
                // 不是服务端故障：降到 debug 且不堆栈，免得攻击者反复投喂垃圾 Token 就能刷满错误日志
                logger.debug("Token 解析失败，按未认证处理: " + e.getMessage());
            }
        }

        // 如果提取到了邮箱且当前上下文没有认证信息
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = null;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(email);
            } catch (Exception e) {
                // 用户不存在或已被禁用等情况：保持未认证状态，由后续安全规则返回 401/403
                logger.warn("加载用户信息失败，保持未认证: " + e.getMessage());
            }
            // 验证 Token 是否有效，再看它是否签发于该账号的失效水位之前（重置过口令的账号，旧 token 到此为止）
            if (userDetails != null && jwtUtils.validateToken(jwt, userDetails.getUsername())
                    && !isRevokedSafe(email, jwt)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 将认证信息存入 Security 上下文
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 失效水位挂在 Redis 上：抖动或宕机时异常不能冲出过滤器，否则所有带 token 的请求都被打成 500。
     * 查询失败按「未作废」放行（fail-open）——水位只是改密/重置口令后的补充防线，
     * 签名校验与账号状态检查仍在前两步，不会因此放过无效凭证
     */
    private boolean isRevokedSafe(String email, String jwt) {
        try {
            return tokenRevocationService.isRevoked(email, jwtUtils.extractIssuedAt(jwt));
        } catch (Exception e) {
            logger.warn("查询 token 失效水位失败，按未作废放行: " + e.getMessage());
            return false;
        }
    }
}
