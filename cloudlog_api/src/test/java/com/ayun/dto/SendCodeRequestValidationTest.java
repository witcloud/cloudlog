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
 * 发码入参校验：邮箱格式与长度上限都声明在 DTO 上，规则与注册侧同源。
 * 一枚格式错的邮箱不该走到「发一封真邮件 + 占一次该邮箱的频控额度」那一步
 */
class SendCodeRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /** 与 AuthController 的两条发码路径同形：邮箱从查询参数改成请求体后，@Valid 失败仍由全局处理器转 400 + 中文提示 */
    @RestController
    static class ProbeController {
        @PostMapping("/probe/code")
        void sendCode(@Valid @RequestBody SendCodeRequest request) {
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private SendCodeRequest request(String email) {
        SendCodeRequest request = new SendCodeRequest();
        request.setEmail(email);
        return request;
    }

    private Set<String> messagesOf(SendCodeRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("合规邮箱不产生任何校验错误")
    void validEmailPasses() {
        assertTrue(messagesOf(request("reader@ayun.com")).isEmpty());
    }

    @Test
    @DisplayName("空邮箱与缺 @ 的邮箱分别按「不能为空」「格式不正确」拒绝，提示语与注册侧同一句")
    void blankAndMalformedEmailAreRejected() {
        assertEquals(Set.of("邮箱不能为空"), messagesOf(request("")));
        assertEquals(Set.of("邮箱格式不正确"), messagesOf(request("reader-at-ayun")));
    }

    @Test
    @DisplayName("邮箱长度上限与列宽一致，超长在发码前就被拒")
    void overLongEmailIsRejected() {
        Set<String> messages = messagesOf(request("a".repeat(247) + "@ayun.com"));

        assertTrue(messages.contains("邮箱长度不能超过 255 个字符"), "实际为 " + messages);
    }

    @Test
    @DisplayName("发码接口收到格式错的邮箱时返回 400，响应体仍是 Result 结构的中文提示")
    void malformedEmailReachesClientAsBadRequest() throws Exception {
        mvc.perform(post("/probe/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reader-at-ayun\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
    }
}
