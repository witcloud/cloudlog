package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.dto.EmailChangeRequest;
import com.ayun.dto.PasswordChangeRequest;
import com.ayun.entity.User;
import com.ayun.mapper.UserMapper;
import com.ayun.security.TokenRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户服务单元测试：覆盖邮箱修改的三重校验（口令 + 唯一性 + 验证码）与一次性验证码的消费时机，
 * 以及口令修改的「先验当前口令、只回写口令一列、改完推失效水位」三步
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenRevocationService tokenRevocationService;

    /** 头像换下去之后那一步对象回收的协作者（business-model.md §3.3） */
    @Mock
    private ImageReclaimService imageReclaimService;

    @InjectMocks
    private UserService userService;

    private static final String CURRENT_EMAIL = "ayun@example.com";
    private static final String NEW_EMAIL = "new@example.com";
    private static final String RAW_PASSWORD = "123456";
    private static final String NEW_RAW_PASSWORD = "ayun2026new";
    private static final String CODE = "123456";

    private User currentUser() {
        return User.builder()
                .id(1L)
                .email(CURRENT_EMAIL)
                .password("ENC_PWD")
                .username("ayun")
                .role("user")
                .status(1)
                .build();
    }

    private EmailChangeRequest request(String newEmail, String code, String password) {
        EmailChangeRequest req = new EmailChangeRequest();
        req.setNewEmail(newEmail);
        req.setCode(code);
        req.setPassword(password);
        return req;
    }

    // ---- 成功路径：三重校验全过，仅改 email 一列 ----

    @Test
    @DisplayName("修改邮箱：口令、唯一性、验证码全过后只更新 email 列")
    void changeEmailUpdatesOnlyEmailOnSuccess() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(currentUser());
        when(passwordEncoder.matches(RAW_PASSWORD, "ENC_PWD")).thenReturn(true);
        when(userMapper.findByEmail(NEW_EMAIL)).thenReturn(null);
        when(verificationCodeService.verifyEmailChangeCode(NEW_EMAIL, CODE)).thenReturn(true);

        userService.changeEmail(CURRENT_EMAIL, request(NEW_EMAIL, CODE, RAW_PASSWORD));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User saved = captor.getValue();
        assertEquals(1, saved.getId().intValue());
        assertEquals(NEW_EMAIL, saved.getEmail());
        assertNull(saved.getPassword(), "不得连带改写口令列");
        assertNull(saved.getRole(), "不得连带改写角色列");
        assertNull(saved.getStatus(), "不得连带改写状态列");
    }

    // ---- 失败路径：验证码错误 / 过期 ----

    @Test
    @DisplayName("修改邮箱：验证码错误或过期时拒绝且不改库")
    void changeEmailRejectsBadOrExpiredCode() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(currentUser());
        when(passwordEncoder.matches(RAW_PASSWORD, "ENC_PWD")).thenReturn(true);
        when(userMapper.findByEmail(NEW_EMAIL)).thenReturn(null);
        when(verificationCodeService.verifyEmailChangeCode(NEW_EMAIL, "000000")).thenReturn(false);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> userService.changeEmail(CURRENT_EMAIL, request(NEW_EMAIL, "000000", RAW_PASSWORD)));

        assertEquals("验证码错误或已过期", error.getMessage());
        verify(userMapper, never()).update(any());
    }

    // ---- 失败路径：新邮箱已被占用，且不消费验证码（改绑失败后用户无需重新收码） ----

    @Test
    @DisplayName("修改邮箱：新邮箱已被占用时拒绝，且验证码不被消费")
    void changeEmailRejectsOccupiedEmailWithoutConsumingCode() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(currentUser());
        when(passwordEncoder.matches(RAW_PASSWORD, "ENC_PWD")).thenReturn(true);
        when(userMapper.findByEmail(NEW_EMAIL)).thenReturn(User.builder().id(2L).email(NEW_EMAIL).build());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> userService.changeEmail(CURRENT_EMAIL, request(NEW_EMAIL, CODE, RAW_PASSWORD)));

        assertEquals("该邮箱已被注册", error.getMessage());
        verify(verificationCodeService, never()).verifyEmailChangeCode(anyString(), anyString());
        verify(userMapper, never()).update(any());
    }

    // ---- 失败路径：当前口令不符（防未授权者持有效验证码改他人邮箱） ----

    @Test
    @DisplayName("修改邮箱：当前口令不符时拒绝，不进入发码与改库")
    void changeEmailRejectsWrongPassword() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(currentUser());
        when(passwordEncoder.matches("wrong", "ENC_PWD")).thenReturn(false);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> userService.changeEmail(CURRENT_EMAIL, request(NEW_EMAIL, CODE, "wrong")));

        assertEquals("当前密码错误", error.getMessage());
        verify(verificationCodeService, never()).verifyEmailChangeCode(anyString(), anyString());
        verify(userMapper, never()).update(any());
    }

    // ---- 发码前置：唯一性在投递前拦截，避免向已占用邮箱发信 ----

    @Test
    @DisplayName("发码：新邮箱已被占用时拒绝，不投递验证码")
    void sendEmailChangeCodeRejectsOccupiedEmail() {
        when(userMapper.findByEmail(NEW_EMAIL)).thenReturn(User.builder().id(2L).email(NEW_EMAIL).build());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> userService.sendEmailChangeCode(NEW_EMAIL));

        assertEquals("该邮箱已被注册", error.getMessage());
        verify(verificationCodeService, never()).sendEmailChangeCode(anyString());
    }

    @Test
    @DisplayName("发码：新邮箱未被占用时投递验证码")
    void sendEmailChangeCodeSendsForFreeEmail() {
        when(userMapper.findByEmail(NEW_EMAIL)).thenReturn(null);

        userService.sendEmailChangeCode(NEW_EMAIL);

        verify(verificationCodeService).sendEmailChangeCode(NEW_EMAIL);
    }

    // ---- 修改口令：先验当前口令，只回写口令一列，成功后把该账号的失效水位推到此刻 ----

    private PasswordChangeRequest changeRequest(String oldPassword, String newPassword) {
        PasswordChangeRequest req = new PasswordChangeRequest();
        req.setOldPassword(oldPassword);
        req.setNewPassword(newPassword);
        return req;
    }

    @Test
    @DisplayName("修改口令：当前口令验过后只更新 password 列，并推一次失效水位")
    void changePasswordWritesOnlyPasswordColumnAndRaisesEpoch() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(currentUser());
        when(passwordEncoder.matches(RAW_PASSWORD, "ENC_PWD")).thenReturn(true);
        when(passwordEncoder.matches(NEW_RAW_PASSWORD, "ENC_PWD")).thenReturn(false);
        when(passwordEncoder.encode(NEW_RAW_PASSWORD)).thenReturn("ENC_NEW");

        userService.changePassword(CURRENT_EMAIL, changeRequest(RAW_PASSWORD, NEW_RAW_PASSWORD));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User saved = captor.getValue();
        assertEquals(1, saved.getId().intValue());
        assertEquals("ENC_NEW", saved.getPassword(), "落库的必须是散列值而非明文");
        assertNull(saved.getEmail(), "不得连带改写邮箱列");
        assertNull(saved.getUsername(), "不得连带改写用户名列");
        assertNull(saved.getRole(), "不得连带改写角色列");
        assertNull(saved.getStatus(), "不得连带改写状态列");
        // 邮箱没变，旧 token 的 subject 仍查得到用户，只有水位能作废它
        verify(tokenRevocationService).revokeBeforeNow(CURRENT_EMAIL);
        verifyNoInteractions(verificationCodeService);
    }

    @Test
    @DisplayName("修改口令：当前口令不符时拒绝，不编码、不改库、不推水位")
    void changePasswordRejectsWrongCurrentPassword() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(currentUser());
        when(passwordEncoder.matches("wrong", "ENC_PWD")).thenReturn(false);

        BizException error = assertThrows(BizException.class,
                () -> userService.changePassword(CURRENT_EMAIL, changeRequest("wrong", NEW_RAW_PASSWORD)));

        assertEquals("当前密码错误", error.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userMapper, never()).update(any());
        verify(tokenRevocationService, never()).revokeBeforeNow(anyString());
    }

    @Test
    @DisplayName("修改口令：新口令与当前口令相同时拒绝，免得白踢一次会话却什么都没改")
    void changePasswordRejectsPasswordEqualToCurrent() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(currentUser());
        when(passwordEncoder.matches(RAW_PASSWORD, "ENC_PWD")).thenReturn(true);

        BizException error = assertThrows(BizException.class,
                () -> userService.changePassword(CURRENT_EMAIL, changeRequest(RAW_PASSWORD, RAW_PASSWORD)));

        assertEquals("新密码不能与当前密码相同", error.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userMapper, never()).update(any());
        verify(tokenRevocationService, never()).revokeBeforeNow(anyString());
    }

    @Test
    @DisplayName("修改口令：会话里的邮箱已查不到用户时拒绝，不碰口令编码也不推水位")
    void changePasswordRejectsUnknownAccount() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> userService.changePassword(CURRENT_EMAIL, changeRequest(RAW_PASSWORD, NEW_RAW_PASSWORD)));

        assertEquals("未找到当前用户信息", error.getMessage());
        verifyNoInteractions(passwordEncoder);
        verify(userMapper, never()).update(any());
        verify(tokenRevocationService, never()).revokeBeforeNow(anyString());
    }

    // ---- 头像绑定：换下去的旧对象交给回收（business-model.md §3.3）----

    private static final String OLD_AVATAR = "http://127.0.0.1:9000/cloudlog/avatar/202609/old.jpeg";
    private static final String NEW_AVATAR = "http://127.0.0.1:9000/cloudlog/avatar/202610/new.jpeg";

    @Test
    @DisplayName("换头像：绑定成功后把旧地址交给对象回收")
    void updateAvatarReclaimsPreviousObject() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(
                User.builder().id(1L).email(CURRENT_EMAIL).avatar(OLD_AVATAR).build());

        userService.updateAvatar(CURRENT_EMAIL, NEW_AVATAR);

        ArgumentCaptor<User> written = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(written.capture());
        assertEquals(NEW_AVATAR, written.getValue().getAvatar());
        verify(imageReclaimService).reclaim(List.of(OLD_AVATAR));
    }

    @Test
    @DisplayName("首次设头像：没有旧对象，不产生回收")
    void updateAvatarWithoutPreviousReclaimsNothing() {
        when(userMapper.findByEmail(CURRENT_EMAIL)).thenReturn(
                User.builder().id(1L).email(CURRENT_EMAIL).build());

        userService.updateAvatar(CURRENT_EMAIL, NEW_AVATAR);

        verify(imageReclaimService, never()).reclaim(any());
    }
}
