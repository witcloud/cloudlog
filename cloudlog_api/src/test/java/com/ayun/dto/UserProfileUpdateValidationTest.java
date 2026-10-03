package com.ayun.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 改资料的用户名规则：注册侧限 2-30 位并带字符白名单，改资料侧过去是 2-20 位且什么都不限，
 * 同一个字段两套口径。两处现在都引 {@link UsernameRules}，这里钉住「同一个名字在两个入口判定一致」
 */
class UserProfileUpdateValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private UserProfileUpdateDTO profile(String username) {
        UserProfileUpdateDTO dto = new UserProfileUpdateDTO();
        dto.setUsername(username);
        dto.setBio("记录技术与生活");
        dto.setOccupation("后端工程师");
        return dto;
    }

    private RegisterRequest register(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("reader@ayun.com");
        request.setUsername(username);
        request.setPassword("ayun2026");
        request.setCode("123456");
        return request;
    }

    private Set<String> usernameMessagesOf(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .filter(message -> message.startsWith("用户名"))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("长度档抬到与注册一致：21-30 位的名字在改资料时不再被误拒")
    void longerUsernamesAreNowAccepted() {
        assertTrue(VALIDATOR.validate(profile("a".repeat(25))).isEmpty());
        assertEquals(Set.of("用户名长度需在 2-30 位之间"), usernameMessagesOf(profile("a")));
        assertEquals(Set.of("用户名长度需在 2-30 位之间"), usernameMessagesOf(profile("b".repeat(31))));
    }

    @Test
    @DisplayName("白名单同样生效：带空格或 @ 的用户名在改资料时被拒，提示语与注册侧同一句")
    void charsetWhitelistApplies() {
        assertEquals(Set.of("用户名仅支持中文、字母、数字、下划线和中划线"), usernameMessagesOf(profile("read er")));
        assertEquals(Set.of("用户名仅支持中文、字母、数字、下划线和中划线"), usernameMessagesOf(profile("reader@host")));
    }

    @Test
    @DisplayName("同一组用户名在注册与改资料两个入口得到完全相同的判定")
    void bothEntriesShareOneRule() {
        for (String username : List.of("a", "re", "b".repeat(30), "b".repeat(31), "read er", "reader@host", "阿云_reader-01")) {
            assertEquals(usernameMessagesOf(register(username)), usernameMessagesOf(profile(username)),
                    "用户名「" + username + "」在两处的判定不该不同");
        }
    }
}
