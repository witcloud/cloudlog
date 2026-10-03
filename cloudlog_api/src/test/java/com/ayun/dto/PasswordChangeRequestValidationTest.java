package com.ayun.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 修改口令的参数校验：长度档与 RegisterRequest 同一条线，弱口令必须在落到 BCrypt 之前就被拒掉；
 * 当前口令为空同样拦在入参层，不给「不带旧口令只带新口令」的请求留通道
 */
class PasswordChangeRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messagesOf(String oldPassword, String newPassword) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("合规的当前口令与新口令不产生任何校验错误")
    void validRequestPasses() {
        assertTrue(messagesOf("123456", "ayun2026").isEmpty());
    }

    @Test
    @DisplayName("新口令短于 8 位或超过 72 位都被拒，提示中文")
    void newPasswordLengthBounds() {
        assertEquals(Set.of("密码长度需在 8-72 位之间"), messagesOf("123456", "ayun202"));
        assertEquals(Set.of("密码长度需在 8-72 位之间"), messagesOf("123456", "a".repeat(73)));
    }

    @Test
    @DisplayName("不带当前口令的请求被拒：改口令必须先证明持有会话")
    void blankCurrentPasswordIsRejected() {
        assertTrue(messagesOf("", "ayun2026").contains("当前密码不能为空"));
    }
}
