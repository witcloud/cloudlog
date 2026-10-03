package com.ayun.model.dto;

import com.ayun.common.GlobalExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分页入参的上下限：page / pageSize 是公开 URL 上谁都能改的查询参数，
 * 不给上限就等于一次 ?pageSize=100000 把整张表装进响应，给下限则是为了让「页码不能小于 1」这句话能传到人面前
 */
class PageBoundsValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /** 与 ArticleController.getArticles 同形：GET 的查询参数绑到 DTO，@Valid 才生效 */
    @RestController
    static class ProbeController {
        @GetMapping("/probe/articles")
        void list(@Valid PostQueryDTO queryDTO) {
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private Set<String> messagesOf(int page, int pageSize) {
        PostQueryDTO query = new PostQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        return VALIDATOR.validate(query).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("缺省的分页参数本身就是合法的")
    void defaultsPass() {
        assertTrue(messagesOf(1, 10).isEmpty());
        assertTrue(messagesOf(3, 50).isEmpty(), "50 是允许的上限，不是越界值");
    }

    @Test
    @DisplayName("页容量越上下限时，提示写的是具体上限而不是「参数不合法」")
    void pageSizeBoundsAreReported() {
        assertEquals(Set.of("每页条数不能超过 50"), messagesOf(1, 51));
        assertEquals(Set.of("每页条数不能小于 1"), messagesOf(1, 0));
    }

    @Test
    @DisplayName("页码为 0 被拒")
    void zeroPageIsRejected() {
        assertEquals(Set.of("页码不能小于 1"), messagesOf(0, 10));
    }

    @Test
    @DisplayName("GET 查询参数越界时返回 400 并带上中文提示：绑定失败抛的是 BindException，不被接住就成了默认错误页")
    void oversizePageSizeReachesClientAsBadRequest() throws Exception {
        mvc.perform(get("/probe/articles").param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("每页条数不能超过 50"));
    }
}
