package com.ayun.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端来源地址：登录防爆破与阅读量去重都要按来源地址分键，取值口径必须一致。
 * X-Forwarded-For 是客户端自己就能写的请求头，直连部署时采信它等于让爆破者随手换一个计数键、
 * 也让登录与访问日志记下假地址，所以是否采信由部署形态决定（app.security.trust-forwarded-for），默认不采信
 */
@Component
public class IpUtils {

    /** 是否采信 X-Forwarded-For：只有当本进程确实挂在可信反向代理之后才为真（见 application.example.yml 的 Nginx 参考配置） */
    private final boolean trustForwardedFor;

    public IpUtils(@Value("${app.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    /**
     * 来源地址：不采信代理头时一律取直连地址；采信时取 X-Forwarded-For 的首值
     *（后续几级是代理自己加的，链路顺序同样由客户端可控，不采信）
     * @return 来源地址
     */
    public String clientIp() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        if (!trustForwardedFor) {
            return request.getRemoteAddr();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
