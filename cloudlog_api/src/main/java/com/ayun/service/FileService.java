package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** 对象 key 中的归档层级，如 202610 */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 魔数比对读的文件头长度：webp 的格式标识落在第 9-12 字节，16 字节可容纳全部四种文件头 */
    private static final int MAGIC_HEADER_LENGTH = 16;

    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] MAGIC_GIF = {0x47, 0x49, 0x46};
    private static final byte[] MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};

    /** 与 UploadController 的 Content-Type 快速过滤同一句话术，不让同一件事出现两套说法 */
    private static final String UNSUPPORTED_IMAGE = "不支持的文件格式，请上传图片 (jpg, png, gif, webp)";

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 上传文件到 MinIO。
     * 真实格式只认文件头：Content-Type 与文件名都由客户端自报，改一个字节就能冒充，
     * 所以扩展名不取 originalFilename，一律按魔数由服务端生成
     *
     * @param file     文件对象
     * @param category 用途前缀（如 avatar、post/cover），取值由调用方固定传入
     * @return 文件的完整访问 URL
     */
    public String upload(MultipartFile file, String category) {
        // 1. 魔数裁决排在任何存储动作之前，非白名单内容一个字节都不入桶
        String extension = extensionOf(readHeader(file));
        if (extension == null) {
            throw new BizException(UNSUPPORTED_IMAGE);
        }

        try {
            // 2. 检查 Bucket 是否存在，不存在则创建
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build());
            }

            // 3. 生成对象 key：{category}/{yyyyMM}/{UUID}{服务端判定的扩展名}
            String objectName = String.format("%s/%s/%s%s", category,
                    YearMonth.now().format(PERIOD_FORMATTER), UUID.randomUUID(), extension);

            // 4. 上传文件
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // 5. 返回完整访问路径 (格式: endpoint/bucketName/objectName)
            return String.format("%s/%s/%s", minioConfig.getEndpoint(), minioConfig.getBucketName(), objectName);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 按文件头判定真实格式
     * @param header 文件前 16 字节
     * @return 服务端生成的扩展名，不在白名单内返回 null
     */
    private static String extensionOf(byte[] header) {
        if (matches(header, 0, MAGIC_JPEG)) {
            return ".jpg";
        }
        if (matches(header, 0, MAGIC_PNG)) {
            return ".png";
        }
        if (matches(header, 0, MAGIC_GIF)) {
            return ".gif";
        }
        // webp 是 RIFF 容器：前 4 字节 RIFF，跳过 4 字节容器长度，第 9-12 字节才是 WEBP
        if (matches(header, 0, MAGIC_RIFF) && matches(header, 8, MAGIC_WEBP)) {
            return ".webp";
        }
        return null;
    }

    /**
     * 读取文件头。读请求体本身失败属于传输层问题，与「格式不合法」分开，交给兜底处理器记堆栈
     */
    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[MAGIC_HEADER_LENGTH];
        try (InputStream input = file.getInputStream()) {
            input.readNBytes(header, 0, MAGIC_HEADER_LENGTH);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
        return header;
    }

    /**
     * 字节序列比对，头部不足魔数长度的短文件一律算不匹配
     */
    private static boolean matches(byte[] header, int offset, byte[] magic) {
        if (header.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
