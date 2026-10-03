package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 上传的格式裁决：Content-Type 与文件名都是客户端自报的，只有文件头说了算；
 * 扩展名由服务端按魔数生成，对象才不会被存成与内容不符的样子
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    private FileService fileService;

    /** 真实 PNG 文件头：89 50 4E 47 + 换行标记 + IHDR */
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 20, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};
    /** 内容是一行文本，却自称 png：魔数比对必须把它拦在桶外 */
    private static final byte[] TEXT = "echo 'rm -rf /'".getBytes();

    @BeforeEach
    void setUp() {
        fileService = new FileService(minioClient, minioConfig);
    }

    /** 让转存走到最后一步：桶已存在，endpoint 与桶名固定 */
    private void stubBucketReady() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioConfig.getBucketName()).thenReturn("cloudlog");
        when(minioConfig.getEndpoint()).thenReturn("http://minio:9000");
    }

    private String objectKeyOf(MinioClient client) throws Exception {
        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client).putObject(args.capture());
        return args.getValue().object();
    }

    @Test
    @DisplayName("伪造 Content-Type 的文本文件被拒，且一个字节都不入桶")
    void forgedContentTypeIsRejectedByMagicCheck() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "image/png", TEXT);

        BizException error = assertThrows(BizException.class, () -> fileService.upload(file, "avatar"));

        assertEquals("不支持的文件格式，请上传图片 (jpg, png, gif, webp)", error.getMessage());
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("真实 PNG 字节入库，扩展名由服务端按文件头判定")
    void realPngPassesEvenWhenFilenameLooksWrong() throws Exception {
        stubBucketReady();
        MockMultipartFile file = new MockMultipartFile("file", "evil.txt", "image/png", PNG);

        String url = fileService.upload(file, "avatar");

        String key = objectKeyOf(minioClient);
        assertTrue(key.startsWith("avatar/"), "对象应按用途前缀归档，实际为 " + key);
        assertTrue(key.endsWith(".png"), "扩展名应取自文件头，实际为 " + key);
        assertTrue(url.endsWith(key), "返回地址应指向刚写入的对象，实际为 " + url);
    }

    @Test
    @DisplayName("四种白名单格式各按自己的文件头定扩展名")
    void eachMagicProducesItsOwnExtension() throws Exception {
        stubBucketReady();
        for (byte[] content : List.of(JPEG, PNG, GIF, WEBP)) {
            fileService.upload(image(content), "post/content");
        }

        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, times(4)).putObject(args.capture());
        List<String> extensions = args.getAllValues().stream()
                .map(PutObjectArgs::object)
                .map(key -> key.substring(key.lastIndexOf('.')))
                .toList();

        assertEquals(List.of(".jpg", ".png", ".gif", ".webp"), extensions,
                "文件名一律写 photo.dat，扩展名只能来自文件头");
    }

    /** 文件名与 Content-Type 都故意与内容不符，用来证明服务端不采信这两处 */
    private MockMultipartFile image(byte[] content) {
        return new MockMultipartFile("file", "photo.dat", "image/jpeg", content);
    }

    @Test
    @DisplayName("RIFF 容器只有第 9-12 字节是 WEBP 时才放行，别的 RIFF 家族（WAV/AVI）照样拒")
    void riffContainerWithoutWebpMarkerIsRejected() {
        byte[] wav = {0x52, 0x49, 0x46, 0x46, 20, 0, 0, 0, 0x57, 0x41, 0x56, 0x45};
        MockMultipartFile file = new MockMultipartFile("file", "sound.wav", "image/webp", wav);

        assertThrows(BizException.class, () -> fileService.upload(file, "post/cover"));
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("不足文件头长度的空壳文件按不合法处理，不越界读取")
    void truncatedHeaderIsRejected() {
        byte[] tooShort = {(byte) 0x89, 0x50, 0x4E};
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", tooShort);

        assertThrows(BizException.class, () -> fileService.upload(file, "avatar"));
    }
}