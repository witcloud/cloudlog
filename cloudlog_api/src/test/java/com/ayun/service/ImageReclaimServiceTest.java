package com.ayun.service;

import com.ayun.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 图片对象回收的两条底线：只删属于本桶的地址（宁可漏删、不可误删），
 * 以及删除动作只排在事务提交之后、失败只留日志（business-model.md §3.3）
 */
@ExtendWith(MockitoExtension.class)
class ImageReclaimServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    private static final String ENDPOINT = "http://127.0.0.1:9000";
    private static final String BUCKET = "cloudlog";

    private static final String OLD_AVATAR = ENDPOINT + "/" + BUCKET + "/avatar/202610/aaaa.jpeg";

    @AfterEach
    void clearTxSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ImageReclaimService service() {
        when(minioConfig.getEndpoint()).thenReturn(ENDPOINT);
        when(minioConfig.getBucketName()).thenReturn(BUCKET);
        return new ImageReclaimService(minioClient, minioConfig);
    }

    // ---- 正文里的地址提取：解析不出图片不报错，因为删文章是主流程 ----

    @Test
    @DisplayName("回收目标 = 封面 + 正文图片语法里的地址，按出现顺序去重")
    void referencedImagesCollectsCoverAndBodyImages() {
        String content = "开头\n\n![](" + OLD_AVATAR + ")\n\n图注\n\n"
                + "![alt](https://cdn.example.com/b.png)\n\n"
                + "[这是链接不是图](https://example.com)\n";

        List<String> targets = ImageReclaimService.referencedImages(
                ENDPOINT + "/" + BUCKET + "/post/cover/202610/cccc.jpg", content);

        assertEquals(List.of(ENDPOINT + "/" + BUCKET + "/post/cover/202610/cccc.jpg",
                OLD_AVATAR, "https://cdn.example.com/b.png"), targets);
    }

    @Test
    @DisplayName("封面又被贴进正文时只回收一次；没有图片、正文为空、语法残缺都只当没有对象")
    void referencedImagesToleratesMissingAndMalformed() {
        assertEquals(List.of(OLD_AVATAR),
                ImageReclaimService.referencedImages(OLD_AVATAR, "正文里又引用了 " + OLD_AVATAR + " 一次"));
        assertEquals(List.of(), ImageReclaimService.referencedImages(null, null));
        assertEquals(List.of(), ImageReclaimService.referencedImages("  ", "没有图片的一行"));
        assertEquals(List.of(), ImageReclaimService.referencedImages(null, "![坏掉的语法(http://a/b.png"));
    }

    // ---- 地址归属裁决：非本桶的一律不碰 ----

    @Test
    @DisplayName("只删 endpoint/bucket/ 打头的地址；第三方图床、相对路径的默认头像、空值全部出局")
    void reclaimDeletesOnlyObjectsOfThisBucket() throws Exception {
        List<String> urls = Arrays.asList(
                OLD_AVATAR,
                "https://api.dicebear.com/9.x/initials/svg?seed=Admin",
                "/default-avatar.svg",
                "https://fanyi-cdn.cdn.bcebos.com/static/cat/asset/logo.png",
                "http://127.0.0.1:9000/cloudlog-x/a.png",
                ENDPOINT + "/" + BUCKET + "/",
                "",
                null);

        service().reclaim(urls);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals(BUCKET, captor.getValue().bucket());
        assertEquals("avatar/202610/aaaa.jpeg", captor.getValue().object());
    }

    @Test
    @DisplayName("桶根平铺的历史对象同样归本桶管，按整段剩余路径当对象键")
    void reclaimHandlesLegacyRootKeys() throws Exception {
        service().reclaim(List.of(ENDPOINT + "/" + BUCKET + "/2e23e016-be1f.jpeg"));

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals("2e23e016-be1f.jpeg", captor.getValue().object());
    }

    @Test
    @DisplayName("单个对象删除失败只记日志，剩下的继续删，异常不外溢")
    void reclaimKeepsGoingAfterFailure() throws Exception {
        doThrow(new RuntimeException("MinIO 不可达")).when(minioClient).removeObject(any());

        ImageReclaimService service = service();
        assertDoesNotThrow(() -> service.reclaim(List.of(
                OLD_AVATAR,
                ENDPOINT + "/" + BUCKET + "/post/content/202610/bbbb.png")));

        verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
    }

    // ---- 时序：提交之后才删 ----

    @Test
    @DisplayName("事务内：回收排在提交点之后，未提交前一个对象都不删")
    void reclaimWaitsUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> reclaimed = new ArrayList<>();

        ImageReclaimService.afterCommit(List.of(OLD_AVATAR), reclaimed::addAll);

        assertEquals(List.of(), reclaimed, "事务还没提交，不该已经去删对象");
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertEquals(List.of(OLD_AVATAR), reclaimed);
    }

    @Test
    @DisplayName("事务回滚：排在提交点之后的回收根本不执行，旧对象安然无恙")
    void rollbackSkipsReclaim() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> reclaimed = new ArrayList<>();

        ImageReclaimService.afterCommit(List.of(OLD_AVATAR), reclaimed::addAll);

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        assertEquals(List.of(), reclaimed);
    }

    @Test
    @DisplayName("不在事务里就没有提交点可等，直接回收；没有目标时什么都不做")
    void reclaimRunsAtOnceWithoutTransaction() {
        List<String> reclaimed = new ArrayList<>();
        ImageReclaimService.afterCommit(List.of(OLD_AVATAR), reclaimed::addAll);
        assertEquals(List.of(OLD_AVATAR), reclaimed);

        List<String> untouched = new ArrayList<>();
        ImageReclaimService.afterCommit(List.of(), untouched::addAll);
        assertEquals(List.of(), untouched);
        verifyNoInteractions(minioClient);
    }
}
