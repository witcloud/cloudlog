package com.ayun.service;

import cn.hutool.core.util.IdUtil;
import com.ayun.common.BizException;
import com.ayun.common.IpUtils;
import com.ayun.dto.LoginRequest;
import com.ayun.dto.PasswordResetRequest;
import com.ayun.dto.RegisterRequest;
import com.ayun.entity.LoginLog;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.LoginPolicyVO;
import com.ayun.security.JwtUtils;
import com.ayun.security.TokenRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证业务逻辑类
 */
@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Autowired
    private LoginLogService loginLogService;

    @Autowired
    private IpUtils ipUtils;

    /** 按「邮箱 + 来源地址」的失败计数键前缀，完整键为 auth:login-fail:{邮箱}:{来源 IP} */
    private static final String LOGIN_FAIL_KEY_PREFIX = "auth:login-fail:";

    /**
     * 只按邮箱的失败计数键前缀。上一条键里有来源地址，轮换 IP 就能对同一个邮箱无限试口令；
     * 这一层不带 IP，堵的正是那条绕法
     */
    private static final String GLOBAL_LOGIN_FAIL_KEY_PREFIX = "auth:login-fail-global:";

    /**
     * 邮箱层的额度下限。阈值取来源地址层配置的 5 倍、但不低于 20：管理员把 max_failures 调到 3 时，
     * 单纯乘 5 会得到 15 次这种过紧的全局额度，正常用户在网吧、公司出口等共享来源地址下会被累加的计数锁住
     */
    private static final int GLOBAL_FAILURES_FLOOR = 20;

    /**
     * 用户注册
     * @param request 注册请求参数
     */
    public void register(RegisterRequest request) {
        // 系统设置关闭注册时拒绝新用户
        if (!settingsService.getBoolean(SettingsService.KEY_REGISTRATION_ENABLED, true)) {
            throw new BizException("当前站点已关闭注册");
        }

        // 两道唯一性排在校验验证码之前：verifyCode 命中即销毁验证码（一次性），
        // 邮箱或用户名已占用属于客户端可自查的输入问题，不该为此烧掉一枚只能重发的码
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new BizException("该邮箱已被注册");
        }
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BizException("该用户名已被占用");
        }

        if (!verificationCodeService.verifyCode(request.getEmail(), request.getCode())) {
            throw new BizException("验证码错误或已过期");
        }

        User user = User.builder()
                // 用户主键与文章同策略：应用层雪花 ID，不暴露注册量、不可遍历（db-design.md §1.1.1）
                .id(IdUtil.getSnowflakeNextId())
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("user")
                .status(1)
                .build();
        userMapper.insert(user);
    }

    /**
     * 用户登录
     * @param request 登录请求参数
     * @return 包含 Token 和用户信息的 Map
     */
    public Map<String, Object> login(LoginRequest request) {
        // 策略在入口读一次就向下传：一次登录最多查两行配置，不该每个分支各查一遍
        LoginPolicyVO policy = settingsService.getLoginPolicy();
        String clientIp = ipUtils.clientIp();
        // UA 与来源地址都只能在请求线程取：日志写入是异步的，换到线程池后请求上下文就空了
        String userAgent = clientUserAgent();
        // 计数键必须用归一化邮箱：MySQL 的 ci 排序规则下 Admin@X.com 与 admin@x.com 命中同一账号，
        // 键不归一就是每个大小写变体各领一套爆破额度，两层锁定形同虚设
        String email = normalizeEmail(request.getEmail());
        String failKey = LOGIN_FAIL_KEY_PREFIX + email + ":" + clientIp;
        String globalFailKey = GLOBAL_LOGIN_FAIL_KEY_PREFIX + email;
        int globalMaxFailures = Math.max(GLOBAL_FAILURES_FLOOR, policy.getMaxFailures() * 5);
        // 两层计数任一到阈即锁：来源地址层挡单点穷举，邮箱层挡换 IP 的分布式穷举。
        // 锁定期间不再校验口令：既不给穷举者「邮箱存在与否」的差异反馈，也不让每次尝试都消耗一次 BCrypt 计算
        String lockedKey = loginFailures(failKey) >= policy.getMaxFailures() ? failKey
                : loginFailures(globalFailKey) >= globalMaxFailures ? globalFailKey : null;
        if (lockedKey != null) {
            loginLogService.recordLogin(request.getEmail(), null, clientIp, userAgent, LoginLog.RESULT_LOCKED);
            throw new BizException(lockedMessage(lockedKey, policy.getLockMinutes()));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            // 账号禁用等其他认证失败不计入爆破计数：它们不是口令猜错
            recordLoginFailure(failKey, policy.getMaxFailures(), policy.getLockMinutes());
            recordLoginFailure(globalFailKey, globalMaxFailures, policy.getLockMinutes());
            loginLogService.recordLogin(request.getEmail(), null, clientIp, userAgent,
                    LoginLog.RESULT_WRONG_PASSWORD);
            throw e;
        } catch (AuthenticationException e) {
            // 禁用账号等其他认证失败：只留一条痕，不计数、原样抛出，401 与提示语仍由全局异常处理器给
            loginLogService.recordLogin(request.getEmail(), null, clientIp, userAgent, LoginLog.RESULT_DISABLED);
            throw e;
        }

        User user = userMapper.findByEmail(request.getEmail());
        // 留痕排在认证成功之后：失败、被锁定、重置口令都不该更新这两列
        userMapper.updateLastLogin(user.getId(), clientIp);
        String token = jwtUtils.generateToken(user.getEmail());

        // 登录成功即两层计数一起清零：只清来源地址那层的话，换 IP 攒下的邮箱计数会一直压着下一个正常登录
        redisTemplate.delete(failKey);
        redisTemplate.delete(globalFailKey);
        loginLogService.recordLogin(request.getEmail(), user.getId(), clientIp, userAgent, LoginLog.RESULT_SUCCESS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("role", user.getRole());
        result.put("user", userInfo);

        return result;
    }

    /**
     * 失败计数交给 Redis 的 INCR 完成，多实例部署下同一键不会丢计数。
     * 两层计数各用自己的阈值走这一条，窗口与锁定时长同源于策略
     */
    private void recordLoginFailure(String failKey, int maxFailures, int lockMinutes) {
        Long failures = redisTemplate.opsForValue().increment(failKey);
        // 首错起一个计数窗口；计满阈值时把窗口按锁定时长重新拉满，锁定才从「锁上」那刻起算
        if (failures != null && (failures == 1L || failures >= maxFailures)) {
            redisTemplate.expire(failKey, lockMinutes * 60L, TimeUnit.SECONDS);
        }
    }

    /**
     * 锁定提示按计数键的剩余 TTL 给分钟数：管理员把时长改成 30 分钟、或锁已经过半，写死的数字都是在报假数。
     * TTL 读不到（键不存在或 Redis 异常）才回落到配置的时长；向上取整——还剩 1 秒也说「1 分钟后再试」，
     * 报 0 分钟等于说没锁
     */
    private String lockedMessage(String failKey, int lockMinutes) {
        Long remainingSeconds = redisTemplate.getExpire(failKey, TimeUnit.SECONDS);
        long seconds = remainingSeconds == null || remainingSeconds <= 0 ? lockMinutes * 60L : remainingSeconds;
        return "尝试次数过多，请 " + (seconds + 59) / 60 + " 分钟后再试";
    }

    /**
     * 当前失败次数。计数由 INCR 写入，经值序列化器读回是数字；键不存在或非数字一律按 0 次放行
     */
    private long loginFailures(String failKey) {
        Object failures = redisTemplate.opsForValue().get(failKey);
        return failures instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 本次请求申报的 User-Agent 原样，供登录日志落库与归类。只从请求头读，不做任何加工
     * （与 {@code ArticleService} 那侧访问日志的取法一致，截断由写入侧按列宽负责）
     */
    private String clientUserAgent() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getHeader("User-Agent");
    }

    /**
     * 登出：把该账号的失效水位推到此刻，早于它签发的 token 全部作废。
     * 水位按账号记而非按单枚 token 拉黑，因此这是「踢掉该账号全部在存会话」——
     * 在没有刷新令牌的体系里按枚吊销做不到，全量作废对「密码可能在别处泄露」的用户反而是更安全的自助手段
     * @param email 账号邮箱，即 JWT 的 subject
     */
    public void logout(String email) {
        tokenRevocationService.revokeBeforeNow(email);
    }

    /**
     * 邮箱归一化：Redis 计数键与验证码键统一用小写去空格形式分键。
     * 库表是 ci 排序规则、查库不区分大小写，键若不归一，大小写变体就是互不感知的独立额度
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * 根据邮箱获取当前登录用户信息
     * @param email 用户邮箱
     * @return 用户对象
     */
    public User getCurrentUser(String email) {
        return userMapper.findByEmail(email);
    }

    /**
     * 发送注册验证码
     * @param email 用户邮箱
     */
    public void sendRegistrationCode(String email) {
        // 与 register() 同一道闸，且排在发码之前：注册已关闭时，直接打这个接口的人
        // 不该先收到一封邮件、占掉一枚码，再在下一步被告知此路不通
        if (!settingsService.getBoolean(SettingsService.KEY_REGISTRATION_ENABLED, true)) {
            throw new BizException("当前站点已关闭注册");
        }
        if (userMapper.findByEmail(email) != null) {
            throw new BizException("该邮箱已被注册");
        }
        verificationCodeService.sendRegistrationCode(email);
    }

    /**
     * 发送重置密码验证码。
     * 邮箱未注册时静默跳过（不抛「该邮箱尚未注册」）：找密链路若区分「已注册/未注册」，
     * 就是一份可批量采集的账号存在性名单。响应一律成功，真实差异只留在登录日志；
     * 投递与频控由 VerificationCodeService 承担
     * @param email 账号邮箱
     */
    public void sendPasswordResetCode(String email) {
        if (userMapper.findByEmail(email) != null) {
            verificationCodeService.sendPasswordResetCode(email);
        }
    }

    /**
     * 凭一次性验证码重置口令。
     * 回库取用户排在校验验证码之前：验证码命中即销毁，先拒掉无账号的请求可避免白烧一次验证码。
     * 改密成功后把该账号的失效水位推到此刻：攻击者手里那枚还在有效期内的 token 随之作废，
     * 调用方无需带 token（这条链路本就是未登录发起的，按 jti 拉黑「当前 token」在这里等于什么都没拉黑）
     * @param request 重置密码参数
     */
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        User user = userMapper.findByEmail(request.getEmail());
        if (user == null) {
            // 与发码侧同口径：未注册邮箱在这里报「验证码错误或已过期」而非「尚未注册」，
            // 不给撞库者任何账号存在性差异。发码已只对已注册邮箱投递，正常路径不可能带有效码走到这里
            throw new BizException("验证码错误或已过期");
        }
        if (!verificationCodeService.verifyPasswordResetCode(request.getEmail(), request.getCode())) {
            throw new BizException("验证码错误或已过期");
        }

        // 只带 id 与 password：UserMapper.xml 的 <set> 跳过 null，避免把 email 等其余列原值回写
        userMapper.update(User.builder()
                .id(user.getId())
                .password(passwordEncoder.encode(request.getNewPassword()))
                .build());
        tokenRevocationService.revokeBeforeNow(request.getEmail());
    }
}
