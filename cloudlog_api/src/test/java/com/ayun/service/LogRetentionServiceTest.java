package com.ayun.service;

import com.ayun.mapper.LoginLogMapper;
import com.ayun.mapper.VisitLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志保留期清理的阈值口径：阈值由服务层算出来当参数绑进 SQL，所以它必须与 `created_at` 同基
 * （那两列是库侧时刻，本站口径为北京墙钟，db-design.md §1）。
 * 「每天 03:30 跑」与「分批删到 0 行为止」是调度与循环形态的问题，这里只断言阈值本身
 */
@ExtendWith(MockitoExtension.class)
class LogRetentionServiceTest {

    @Mock
    private VisitLogMapper visitLogMapper;

    @Mock
    private LoginLogMapper loginLogMapper;

    @InjectMocks
    private LogRetentionService logRetentionService;

    /** 与 LogRetentionService 内联的两个保留天数常量同值 */
    private static final int RETENTION_DAYS = 180;

    @Test
    @DisplayName("保留期阈值按北京墙钟算：JVM 跑 UTC 时不把超期线整体挪 8 小时")
    void purgeThresholdsUseBusinessZone() {
        // 每批都回 0 行：purgeInBatches 的循环第一轮即止，阈值原样留在 captor 里
        when(visitLogMapper.deleteCreatedBefore(any(), anyInt())).thenReturn(0);
        when(loginLogMapper.deleteCreatedBefore(any(), anyInt())).thenReturn(0);
        ArgumentCaptor<LocalDateTime> visitThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> loginThreshold = ArgumentCaptor.forClass(LocalDateTime.class);

        JvmClockProbe.underUtc(() -> {
            LocalDateTime utcBasis = LocalDateTime.now().minusDays(RETENTION_DAYS);
            logRetentionService.purgeExpiredLogs();

            verify(visitLogMapper).deleteCreatedBefore(visitThreshold.capture(), anyInt());
            verify(loginLogMapper).deleteCreatedBefore(loginThreshold.capture(), anyInt());
            JvmClockProbe.assertBeijingWallClock("visit_logs 的超期线", visitThreshold.getValue(), utcBasis);
            JvmClockProbe.assertBeijingWallClock("login_logs 的超期线", loginThreshold.getValue(), utcBasis);
        });
    }
}
