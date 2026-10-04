package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.entity.SiteSetting;
import com.ayun.mapper.SiteSettingMapper;
import com.ayun.model.vo.AboutContentVO;
import com.ayun.model.vo.LoginPolicyVO;
import com.ayun.model.vo.SiteInfoVO;
import com.ayun.model.vo.SystemSettingsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统设置业务逻辑类（站点级配置，键值对存储，缺省值内置）
 */
@Service
public class SettingsService {

    /** 评论审核：开启后新评论需管理员审核通过才展示 */
    public static final String KEY_COMMENT_MODERATION = "comment_moderation";

    /** 开放注册：关闭后新用户无法注册 */
    public static final String KEY_REGISTRATION_ENABLED = "registration_enabled";

    /** 关于页文案：主标题（作者名）/ 身份说明 / 导语 / 头像地址 */
    public static final String KEY_ABOUT_NAME = "about_name";
    public static final String KEY_ABOUT_ROLE = "about_role";
    public static final String KEY_ABOUT_LEAD = "about_lead";
    public static final String KEY_ABOUT_AVATAR = "about_avatar";

    private static final String DEFAULT_ABOUT_NAME = "tcloud";
    private static final String DEFAULT_ABOUT_ROLE = "记录技术与生活";
    private static final String DEFAULT_ABOUT_LEAD =
            "主要写给自己看——把学过、用过、想过的东西记下来，方便回头查。"
                    + "如果恰好也帮到了别人，那就更好。";

    /** 头像没有内置图片资源可回落：未上传时返回空串，由前端使用随发布的 default-avatar.svg */
    private static final String DEFAULT_ABOUT_AVATAR = "";

    /** 站点信息：站点名称（前台标题后缀与页脚落款）/ ICP 备案号（空串即页脚不展示这一行）/ 对外域名 */
    public static final String KEY_SITE_NAME = "site_name";
    public static final String KEY_ICP_NUMBER = "icp_number";

    /**
     * 站点对外域名，sitemap / RSS 里的绝对 URL 与 robots 的 Sitemap 行都由它拼。
     * 库里没有可回落的真值（本机跑的是 localhost，公网域名各人不同），未填即空串，
     * 由 {@code SeoPublicService} 按当次请求的 origin 兜底
     */
    public static final String KEY_SITE_URL = "site_url";

    /** 评论防护：单条字数上限 / 同一用户发布间隔秒数，两项的 0 都表示不限制 */
    public static final String KEY_COMMENT_MAX_LENGTH = "comment_max_length";
    public static final String KEY_COMMENT_INTERVAL_SECONDS = "comment_interval_seconds";

    private static final String DEFAULT_SITE_NAME = "CloudLog";

    /** 备案号取不到可回落的真值：未填即空串，页脚据此不渲染 */
    private static final String DEFAULT_ICP_NUMBER = "";

    /** 对外域名同理没有可内置的真值：未填即空串，由读取方按当次请求的 origin 兜底 */
    private static final String DEFAULT_SITE_URL = "";

    /** 评论防护的内置缺省值，读取处与前台评论框共用这一份 */
    public static final int DEFAULT_COMMENT_MAX_LENGTH = 500;
    public static final int DEFAULT_COMMENT_INTERVAL_SECONDS = 60;

    /** 后台表单与保存共同遵守的取值区间，越界直接拒绝而不是静默夹紧 */
    private static final int COMMENT_MAX_LENGTH_CEILING = 5000;
    private static final int COMMENT_INTERVAL_CEILING = 86400;

    /**
     * 登录锁定策略：连续失败多少次上锁 / 锁多久（分钟）。
     * 缺省值与合法区间都由这里单点持有， AuthService 的登录链路与后台概览卡读的是同一份
     */
    public static final String KEY_LOGIN_MAX_FAILURES = "login_max_failures";
    public static final String KEY_LOGIN_LOCK_MINUTES = "login_lock_minutes";

    public static final int DEFAULT_LOGIN_MAX_FAILURES = 5;
    public static final int DEFAULT_LOGIN_LOCK_MINUTES = 15;
    public static final int LOGIN_MAX_FAILURES_MIN = 3;
    public static final int LOGIN_MAX_FAILURES_MAX = 10;
    public static final int LOGIN_LOCK_MINUTES_MIN = 5;
    public static final int LOGIN_LOCK_MINUTES_MAX = 60;

    @Autowired
    private SiteSettingMapper siteSettingMapper;

    /**
     * 读取布尔型配置，未配置时返回默认值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        SiteSetting setting = siteSettingMapper.findByKey(key);
        if (setting == null || setting.getSettingValue() == null || setting.getSettingValue().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(setting.getSettingValue());
    }

    /**
     * 读取文本型配置，未配置或值为空白时返回默认值
     */
    public String getString(String key, String defaultValue) {
        SiteSetting setting = siteSettingMapper.findByKey(key);
        if (setting == null || setting.getSettingValue() == null || setting.getSettingValue().isBlank()) {
            return defaultValue;
        }
        return setting.getSettingValue();
    }

    /**
     * 读取整数型配置，未配置、值为空白或解析不出整数时回落默认值。
     * 键值表里存的可能是手改过的脏值，这里不能让它把评论发布路径顶成 500
     */
    public int getInt(String key, int defaultValue) {
        SiteSetting setting = siteSettingMapper.findByKey(key);
        if (setting == null || setting.getSettingValue() == null || setting.getSettingValue().isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(setting.getSettingValue().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取全部系统设置
     */
    public SystemSettingsVO getSettings() {
        return SystemSettingsVO.builder()
                .commentModeration(getBoolean(KEY_COMMENT_MODERATION, true))
                .registrationEnabled(getBoolean(KEY_REGISTRATION_ENABLED, true))
                .siteName(getSiteName())
                .icpNumber(getString(KEY_ICP_NUMBER, DEFAULT_ICP_NUMBER))
                .siteUrl(getSiteUrl())
                .commentMaxLength(getInt(KEY_COMMENT_MAX_LENGTH, DEFAULT_COMMENT_MAX_LENGTH))
                .commentIntervalSeconds(getInt(KEY_COMMENT_INTERVAL_SECONDS, DEFAULT_COMMENT_INTERVAL_SECONDS))
                .build();
    }

    /**
     * 更新系统设置（仅更新传入的字段）
     */
    @Transactional
    public void updateSettings(SystemSettingsVO vo) {
        if (vo.getCommentModeration() != null) {
            siteSettingMapper.upsert(KEY_COMMENT_MODERATION, String.valueOf(vo.getCommentModeration()));
        }
        if (vo.getRegistrationEnabled() != null) {
            siteSettingMapper.upsert(KEY_REGISTRATION_ENABLED, String.valueOf(vo.getRegistrationEnabled()));
        }
        // 站点名称与备案号沿用关于页文案的口径：传空串即清空，读取时回落缺省值（备案号则不展示）
        if (vo.getSiteName() != null) {
            siteSettingMapper.upsert(KEY_SITE_NAME, vo.getSiteName().trim());
        }
        if (vo.getIcpNumber() != null) {
            siteSettingMapper.upsert(KEY_ICP_NUMBER, vo.getIcpNumber().trim());
        }
        if (vo.getSiteUrl() != null) {
            String siteUrl = vo.getSiteUrl().trim();
            // 少一段协议就会拼出「cloudlog.site/articles/1」这种链接，爬虫跟着走只会撞 404，
            // 所以在这里拒掉，而不是让 sitemap 静默产出一批坏 URL
            if (!siteUrl.isEmpty() && !siteUrl.startsWith("http://") && !siteUrl.startsWith("https://")) {
                throw new BizException("站点域名需以 http:// 或 https:// 开头");
            }
            siteSettingMapper.upsert(KEY_SITE_URL, siteUrl);
        }
        if (vo.getCommentMaxLength() != null) {
            checkInRange(vo.getCommentMaxLength(), 0, COMMENT_MAX_LENGTH_CEILING, "评论字数上限");
            siteSettingMapper.upsert(KEY_COMMENT_MAX_LENGTH, String.valueOf(vo.getCommentMaxLength()));
        }
        if (vo.getCommentIntervalSeconds() != null) {
            checkInRange(vo.getCommentIntervalSeconds(), 0, COMMENT_INTERVAL_CEILING, "评论发布间隔");
            siteSettingMapper.upsert(KEY_COMMENT_INTERVAL_SECONDS, String.valueOf(vo.getCommentIntervalSeconds()));
        }
    }

    private void checkInRange(Integer value, int min, int max, String label) {
        checkInRange(value, min, max, label, "（0 表示不限制）");
    }

    private void checkInRange(Integer value, int min, int max, String label, String suffix) {
        if (value < min || value > max) {
            throw new BizException(label + "需在 " + min + "-" + max + " 之间" + suffix);
        }
    }

    /**
     * 读取整数型配置并夹进合法区间：未配置或解析不出整数先回落缺省，越界再夹紧。
     * 读取用「夹紧」而保存用「拒绝」，是因为库里的值可能被手改过——登录链路不该为一个 999 分钟的锁定时长崩掉，
     * 也不该让 0 变成「等于不锁」
     */
    public int getIntClamped(String key, int defaultValue, int min, int max) {
        return Math.min(Math.max(getInt(key, defaultValue), min), max);
    }

    /**
     * 当前生效的登录锁定策略，含合法区间。区间随响应下发，前端的范围提示因此只有一份来源
     */
    public LoginPolicyVO getLoginPolicy() {
        return LoginPolicyVO.builder()
                .maxFailures(getIntClamped(KEY_LOGIN_MAX_FAILURES, DEFAULT_LOGIN_MAX_FAILURES,
                        LOGIN_MAX_FAILURES_MIN, LOGIN_MAX_FAILURES_MAX))
                .lockMinutes(getIntClamped(KEY_LOGIN_LOCK_MINUTES, DEFAULT_LOGIN_LOCK_MINUTES,
                        LOGIN_LOCK_MINUTES_MIN, LOGIN_LOCK_MINUTES_MAX))
                .maxFailuresMin(LOGIN_MAX_FAILURES_MIN)
                .maxFailuresMax(LOGIN_MAX_FAILURES_MAX)
                .lockMinutesMin(LOGIN_LOCK_MINUTES_MIN)
                .lockMinutesMax(LOGIN_LOCK_MINUTES_MAX)
                .build();
    }

    /**
     * 保存登录锁定策略（仅写入传入的项，未传的键保持原样）。
     * 越界直接 400 而不是夹紧：保存是一次明确的动作，把 120 分钟悄悄改成 60 会让人以为没生效
     */
    @Transactional
    public void updateLoginPolicy(LoginPolicyVO vo) {
        if (vo.getMaxFailures() != null) {
            checkInRange(vo.getMaxFailures(), LOGIN_MAX_FAILURES_MIN, LOGIN_MAX_FAILURES_MAX,
                    "锁定所需失败次数", "");
            siteSettingMapper.upsert(KEY_LOGIN_MAX_FAILURES, String.valueOf(vo.getMaxFailures()));
        }
        if (vo.getLockMinutes() != null) {
            checkInRange(vo.getLockMinutes(), LOGIN_LOCK_MINUTES_MIN, LOGIN_LOCK_MINUTES_MAX,
                    "锁定时长（分钟）", "");
            siteSettingMapper.upsert(KEY_LOGIN_LOCK_MINUTES, String.valueOf(vo.getLockMinutes()));
        }
    }

    /**
     * 站点名称：前台标题后缀、页脚落款与 SEO 标题回退（business-model.md §2.10）共用这一份，未配置即回落内置默认值
     */
    public String getSiteName() {
        return getString(KEY_SITE_NAME, DEFAULT_SITE_NAME);
    }

    /**
     * 站点对外域名（含协议，末尾不带斜杠）：sitemap / RSS / robots 的绝对 URL 唯一来源。
     * 未配置即空串，调用方自行按请求 origin 兜底
     */
    public String getSiteUrl() {
        return getString(KEY_SITE_URL, DEFAULT_SITE_URL);
    }

    /**
     * 获取公开站点信息（前台布局标题、页脚落款与评论框字数上限共用这一份，未配置时回落内置默认值）
     */
    public SiteInfoVO getSiteInfo() {
        return SiteInfoVO.builder()
                .siteName(getSiteName())
                .icpNumber(getString(KEY_ICP_NUMBER, DEFAULT_ICP_NUMBER))
                .commentMaxLength(getInt(KEY_COMMENT_MAX_LENGTH, DEFAULT_COMMENT_MAX_LENGTH))
                // 间隔秒数一并下发：只在前台留一个能点第二次的按钮，等撞上报错才说明有冷却，
                // 等于把服务端已有的规则藏起来让人试
                .commentIntervalSeconds(
                        getInt(KEY_COMMENT_INTERVAL_SECONDS, DEFAULT_COMMENT_INTERVAL_SECONDS))
                .registrationEnabled(getBoolean(KEY_REGISTRATION_ENABLED, true))
                .build();
    }

    /**
     * 获取关于页文案（未配置的项回落内置默认值）
     */
    public AboutContentVO getAboutContent() {
        return AboutContentVO.builder()
                .name(getString(KEY_ABOUT_NAME, DEFAULT_ABOUT_NAME))
                .role(getString(KEY_ABOUT_ROLE, DEFAULT_ABOUT_ROLE))
                .lead(getString(KEY_ABOUT_LEAD, DEFAULT_ABOUT_LEAD))
                .avatar(getString(KEY_ABOUT_AVATAR, DEFAULT_ABOUT_AVATAR))
                .build();
    }

    /**
     * 更新关于页文案（仅更新传入的字段；传空串即清空该项，读取时回落默认文案）
     */
    @Transactional
    public void updateAboutContent(AboutContentVO vo) {
        putIfPresent(KEY_ABOUT_NAME, vo.getName());
        putIfPresent(KEY_ABOUT_ROLE, vo.getRole());
        putIfPresent(KEY_ABOUT_LEAD, vo.getLead());
        putIfPresent(KEY_ABOUT_AVATAR, vo.getAvatar());
    }

    private void putIfPresent(String key, String value) {
        if (value != null) {
            siteSettingMapper.upsert(key, value.trim());
        }
    }
}
