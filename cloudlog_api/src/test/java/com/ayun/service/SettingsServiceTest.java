package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.entity.SiteSetting;
import com.ayun.mapper.SiteSettingMapper;
import com.ayun.model.vo.LoginPolicyVO;
import com.ayun.model.vo.SiteInfoVO;
import com.ayun.model.vo.SystemSettingsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统设置读写单元测试：只盯两条契约 —— 库里没写过就必须回落到内置默认值（新配置上线即现状），
 * 以及越界值直接拒绝而不是静默夹紧后落库。
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SiteSettingMapper siteSettingMapper;

    @InjectMocks
    private SettingsService settingsService;

    private void stored(String key, String value) {
        when(siteSettingMapper.findByKey(key)).thenReturn(SiteSetting.builder().settingKey(key).settingValue(value).build());
    }

    @Test
    @DisplayName("未写入任何配置时，站点信息与评论防护一律回落内置默认值")
    void unsetSettingsFallBackToBuiltinDefaults() {
        SystemSettingsVO settings = settingsService.getSettings();

        assertEquals("CloudLog", settings.getSiteName());
        assertEquals("", settings.getIcpNumber(), "备案号没有可回落的真值，缺省就是空串（页脚不展示）");
        assertEquals(500, settings.getCommentMaxLength());
        assertEquals(60, settings.getCommentIntervalSeconds());
    }

    @Test
    @DisplayName("公开站点信息是后台设置的只读投影：同样的默认值，不含审核开关")
    void siteInfoMirrorsStoredValues() {
        stored(SettingsService.KEY_SITE_NAME, "我的博客");
        stored(SettingsService.KEY_ICP_NUMBER, "京ICP备2024000000号");
        stored(SettingsService.KEY_COMMENT_MAX_LENGTH, "1000");

        SiteInfoVO info = settingsService.getSiteInfo();

        assertEquals("我的博客", info.getSiteName());
        assertEquals("京ICP备2024000000号", info.getIcpNumber());
        assertEquals(1000, info.getCommentMaxLength());
    }

    @Test
    @DisplayName("存的是脏值（非数字）时按默认值走，不能把评论发布路径顶成 500")
    void unparsableValueFallsBackToDefault() {
        stored(SettingsService.KEY_COMMENT_MAX_LENGTH, "很多");

        assertEquals(500, settingsService.getInt(SettingsService.KEY_COMMENT_MAX_LENGTH, 500));
    }

    @Test
    @DisplayName("越界的评论防护取值被拒绝且不落库，0 与上边界本身合法")
    void outOfRangeValuesAreRefused() {
        assertThrows(BizException.class, () -> settingsService.updateSettings(
                SystemSettingsVO.builder().commentMaxLength(5001).build()));
        assertThrows(BizException.class, () -> settingsService.updateSettings(
                SystemSettingsVO.builder().commentIntervalSeconds(-1).build()));
        verify(siteSettingMapper, never()).upsert(anyString(), anyString());

        settingsService.updateSettings(SystemSettingsVO.builder()
                .commentMaxLength(0).commentIntervalSeconds(86400).build());
        verify(siteSettingMapper).upsert(SettingsService.KEY_COMMENT_MAX_LENGTH, "0");
        verify(siteSettingMapper).upsert(SettingsService.KEY_COMMENT_INTERVAL_SECONDS, "86400");
    }

    @Test
    @DisplayName("站点名称入库前去掉首尾空白，只传部分字段时其余字段不动")
    void siteNameIsTrimmedAndPartialUpdateSkipsAbsentFields() {
        settingsService.updateSettings(SystemSettingsVO.builder().siteName("  我的博客  ").build());

        verify(siteSettingMapper).upsert(SettingsService.KEY_SITE_NAME, "我的博客");
        verify(siteSettingMapper, never()).upsert(eq(SettingsService.KEY_ICP_NUMBER), anyString());
    }

    // ---- 登录锁定策略：读取时夹紧，保存时拒越界 ----

    @Test
    @DisplayName("两键都没写过时回落 5 次 / 15 分钟，合法区间随响应一并给出")
    void loginPolicyFallsBackToBuiltinDefaults() {
        LoginPolicyVO policy = settingsService.getLoginPolicy();

        assertEquals(5, policy.getMaxFailures(), "未配置即沿用防爆破原本的 5 次阈值");
        assertEquals(15, policy.getLockMinutes());
        assertEquals(3, policy.getMaxFailuresMin());
        assertEquals(10, policy.getMaxFailuresMax());
        assertEquals(5, policy.getLockMinutesMin());
        assertEquals(60, policy.getLockMinutesMax());
    }

    @Test
    @DisplayName("库里的手改值越界或不可解析时按区间夹紧：登录链路不该为一个 999 崩掉，也不该锁 0 分钟")
    void storedLoginPolicyIsClampedOnRead() {
        stored(SettingsService.KEY_LOGIN_MAX_FAILURES, "999");
        stored(SettingsService.KEY_LOGIN_LOCK_MINUTES, "1");

        LoginPolicyVO policy = settingsService.getLoginPolicy();

        assertEquals(10, policy.getMaxFailures());
        assertEquals(5, policy.getLockMinutes());
    }

    @Test
    @DisplayName("存的是脏值（非数字）时策略回落默认，不把登录接口顶成 500")
    void unparsableLoginPolicyFallsBackToDefault() {
        stored(SettingsService.KEY_LOGIN_MAX_FAILURES, "一直");

        assertEquals(5, settingsService.getLoginPolicy().getMaxFailures());
    }

    @Test
    @DisplayName("保存越界的锁定策略被拒绝且不落库，两个区间的边界值本身合法")
    void outOfRangeLoginPolicyIsRefused() {
        assertThrows(BizException.class, () -> settingsService.updateLoginPolicy(
                LoginPolicyVO.builder().maxFailures(2).build()));
        assertThrows(BizException.class, () -> settingsService.updateLoginPolicy(
                LoginPolicyVO.builder().maxFailures(11).build()));
        assertThrows(BizException.class, () -> settingsService.updateLoginPolicy(
                LoginPolicyVO.builder().lockMinutes(120).build()));
        verify(siteSettingMapper, never()).upsert(anyString(), anyString());

        settingsService.updateLoginPolicy(LoginPolicyVO.builder().maxFailures(3).lockMinutes(60).build());
        verify(siteSettingMapper).upsert(SettingsService.KEY_LOGIN_MAX_FAILURES, "3");
        verify(siteSettingMapper).upsert(SettingsService.KEY_LOGIN_LOCK_MINUTES, "60");
    }

    @Test
    @DisplayName("只改一项时另一键不动：两键各自独立 upsert")
    void partialLoginPolicyUpdateLeavesTheOtherKeyAlone() {
        settingsService.updateLoginPolicy(LoginPolicyVO.builder().lockMinutes(30).build());

        verify(siteSettingMapper).upsert(SettingsService.KEY_LOGIN_LOCK_MINUTES, "30");
        verify(siteSettingMapper, never()).upsert(eq(SettingsService.KEY_LOGIN_MAX_FAILURES), anyString());
    }
}
