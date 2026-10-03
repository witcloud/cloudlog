package com.ayun.config;

import com.ayun.model.vo.PostIdVO;
import com.ayun.model.vo.PostVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson 全局序列化配置测试：文章雪花 ID 必须以字符串下发（19 位 Long 进 JS 会丢精度），
 * Integer 计数列不受影响
 */
class JacksonConfigTest {

    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longToStringSerializer().customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("Long 文章 ID 序列化为字符串")
    void serializesArticleIdAsString() throws Exception {
        PostIdVO vo = PostIdVO.builder().id(7123456789012345678L).build();

        assertEquals("{\"id\":\"7123456789012345678\"}", objectMapper().writeValueAsString(vo));
    }

    @Test
    @DisplayName("文章 VO 的雪花 ID 是字符串，Integer 计数仍是数字")
    void keepsCountersAsNumbers() throws Exception {
        PostVO vo = new PostVO();
        vo.setId(7123456789012345678L);
        vo.setViews(7);
        vo.setTags(List.of());

        String json = objectMapper().writeValueAsString(vo);

        assertTrue(json.contains("\"id\":\"7123456789012345678\""), json);
        assertTrue(json.contains("\"views\":7"), json);
    }
}
