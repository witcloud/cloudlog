package com.ayun.dto;

import com.ayun.common.GlobalExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册参数校验：长度与字符集边界都在 DTO 上声明，弱口令必须在落到验证码与 BCrypt 之前就被拒掉
 */
class RegisterRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /** 与 AuthController 的注册入参同形：@Valid 失败由全局处理器转 400 + 中文提示 */
    @RestController
    static class ProbeController {
        @PostMapping("/probe/register")
        void register(@Valid @RequestBody RegisterRequest request) {
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private RegisterRequest request(String email, String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword(password);
        request.setCode("123456");
        return request;
    }

    private Set<String> messagesOf(RegisterRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("合规注册参数不产生任何校验错误")
    void validRequestPasses() {
        assertTrue(messagesOf(request("reader@ayun.com", "阿云_reader-01", "ayun2026")).isEmpty());
    }

    @Test
    @DisplayName("弱口令（少于 8 位）被拒，提示中文")
    void shortPasswordIsRejected() {
        assertEquals(Set.of("密码长度需在 8-72 位之间"),
                messagesOf(request("reader@ayun.com", "reader", "1234")));
    }

    @Test
    @DisplayName("超过 72 位的密码被拒：BCrypt 会截断第 72 位之后的内容，不能让用户误以为整串都受保护")
    void overLongPasswordIsRejected() {
        assertEquals(Set.of("密码长度需在 8-72 位之间"),
                messagesOf(request("reader@ayun.com", "reader", "a".repeat(73))));
    }

    @Test
    @DisplayName("用户名限 2-30 位，且只允许中文、字母、数字、下划线、中划线")
    void usernameCharsetAndLength() {
        assertEquals(Set.of("用户名长度需在 2-30 位之间"),
                messagesOf(request("reader@ayun.com", "a", "ayun2026")));
        assertEquals(Set.of("用户名长度需在 2-30 位之间"),
                messagesOf(request("reader@ayun.com", "b".repeat(31), "ayun2026")));
        assertEquals(Set.of("用户名仅支持中文、字母、数字、下划线和中划线"),
                messagesOf(request("reader@ayun.com", "read er", "ayun2026")));
        assertEquals(Set.of("用户名仅支持中文、字母、数字、下划线和中划线"),
                messagesOf(request("reader@ayun.com", "reader@host", "ayun2026")));
    }

    @Test
    @DisplayName("邮箱长度上限与列宽一致，超长在写库前就被拒")
    void overLongEmailIsRejected() {
        Set<String> messages = messagesOf(request("a".repeat(247) + "@ayun.com", "reader", "ayun2026"));

        assertTrue(messages.contains("邮箱长度不能超过 255 个字符"), "实际为 " + messages);
    }

    @Test
    @DisplayName("注册接口带弱口令时返回 400，响应体仍是 Result 结构的中文提示")
    void weakPasswordReachesClientAsBadRequest() throws Exception {
        mvc.perform(post("/probe/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reader@ayun.com\",\"username\":\"reader\","
                                + "\"password\":\"1234\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度需在 8-72 位之间"));
    }
}
