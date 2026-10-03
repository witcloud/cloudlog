package com.ayun.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLSyntaxErrorException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器的分派边界：框架级异常不得把内部细节带回响应，业务异常的话术必须原样保留
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/db")
        void db() {
            throw new InvalidDataAccessResourceUsageException(
                    "Statement id=[com.ayun.mapper.FollowMapper.countByAuthor] ; bad SQL grammar"
                            + " [Table 'cloudlog.follows' doesn't exist]",
                    new SQLSyntaxErrorException("Table 'cloudlog.follows' doesn't exist"));
        }

        @GetMapping("/probe/business")
        void business() {
            throw new BizException("文章不存在");
        }

        @GetMapping("/probe/system")
        void system() {
            throw new RuntimeException("文件上传失败", new java.io.IOException("MinIO 连接中断"));
        }

        @GetMapping("/probe/duplicate")
        void duplicate() {
            throw new DuplicateKeyException("Duplicate entry for key 'users.email'");
        }

        @GetMapping("/probe/missing")
        void missing() {
            throw new NotFoundException("文章不存在或无权访问");
        }

        @GetMapping("/probe/id")
        void id(@RequestParam Integer id) {
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("数据库异常只回通用文案，表名与 SQL 不外泄")
    void dataAccessExceptionIsNotLeakedToClient() throws Exception {
        mvc.perform(get("/probe/db"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }

    @Test
    @DisplayName("BizException 的提示语原样返回，仍是 400")
    void businessMessageSurvives() throws Exception {
        mvc.perform(get("/probe/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文章不存在"));
    }

    @Test
    @DisplayName("未归类的 RuntimeException 不再把内部话术带回客户端，改回 500 并记堆栈")
    void unclassifiedRuntimeBecomesUnavailable() throws Exception {
        mvc.perform(get("/probe/system"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }

    @Test
    @DisplayName("唯一键冲突冒到接口层时按占用回 400，不走 DataAccessException 的 500 兜底")
    void duplicateKeyIsABusinessRejection() throws Exception {
        mvc.perform(get("/probe/duplicate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱或用户名已被占用"));
    }

    @Test
    @DisplayName("NotFoundException 仍是 404，前端据此区分「文章不存在」与「加载失败」")
    void notFoundKeepsStatus404() throws Exception {
        mvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("文章不存在或无权访问"));
    }

    @Test
    @DisplayName("参数类型转换失败只回通用文案，不暴露 Java 类型名")
    void typeMismatchIsNotLeakedToClient() throws Exception {
        mvc.perform(get("/probe/id").param("id", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }
}
