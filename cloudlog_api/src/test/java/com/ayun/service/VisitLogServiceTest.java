package com.ayun.service;

import com.ayun.entity.VisitLog;
import com.ayun.mapper.VisitLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 访问日志写入侧的三条边界：bot 不落库、超长 UA 截到列宽、写失败绝不往上抛
 * （抛出去就会砸穿文章详情接口，而日志只是旁路）
 */
@ExtendWith(MockitoExtension.class)
class VisitLogServiceTest {

    private static final String DESKTOP_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Mock
    private VisitLogMapper visitLogMapper;

    @InjectMocks
    private VisitLogService visitLogService;

    @Test
    @DisplayName("真实浏览器访问落一行，created_at 不赋值交给库默认值")
    void insertsOneRowPerVisit() {
        visitLogService.recordVisit(7123456789012345678L, "112.10.20.30", DESKTOP_CHROME);

        ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
        verify(visitLogMapper).insert(captor.capture());
        VisitLog saved = captor.getValue();
        assertEquals(7123456789012345678L, saved.getPostId().longValue());
        assertEquals("112.10.20.30", saved.getIp());
        assertEquals(DESKTOP_CHROME, saved.getUserAgent());
        assertNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("bot 与空 UA 都不落库")
    void skipsBots() {
        visitLogService.recordVisit(1L, "112.10.20.30", "Mozilla/5.0 (compatible; Googlebot/2.1)");
        visitLogService.recordVisit(1L, "112.10.20.30", "curl/8.4.0");
        visitLogService.recordVisit(1L, "112.10.20.30", null);

        verifyNoInteractions(visitLogMapper);
    }

    @Test
    @DisplayName("UA 超出列宽时截到 255，而不是让整条 INSERT 失败")
    void truncatesOverlongUserAgent() {
        String overlong = "Mozilla/5.0 " + "x".repeat(400);

        visitLogService.recordVisit(1L, "112.10.20.30", overlong);

        ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
        verify(visitLogMapper).insert(captor.capture());
        assertEquals(255, captor.getValue().getUserAgent().length());
    }

    @Test
    @DisplayName("写库失败只吞掉异常：调用方是文章详情，不能因为日志挂了就返回失败")
    void swallowsInsertFailure() {
        doThrow(new RuntimeException("连接断开")).when(visitLogMapper).insert(any());

        assertDoesNotThrow(() -> visitLogService.recordVisit(1L, "112.10.20.30", DESKTOP_CHROME));
    }
}
