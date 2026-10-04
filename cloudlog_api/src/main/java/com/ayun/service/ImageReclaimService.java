package com.ayun.service;

import com.ayun.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片对象回收：头像被替换、文章被删除之后，把随之失去引用的对象从桶里删掉
 * （business-model.md §3.3）。总口径是**宁可漏删、不可误删**——地址认不准就不删，删除失败只留日志。
 * 不做全桶扫描与孤儿对象巡检（§6）：只回收这两条链路明确交进来的地址
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageReclaimService {

    /**
     * Markdown 图片 {@code ![alt](url)}，句法与 {@code ExcerptUtils.IMAGE} 同一条；
     * 差别是这里要把地址留在捕获组里——摘要只需抹掉整段，回收要知道抹掉的是哪个对象
     */
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\(\\s*([^)\\s]+)");

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 一篇文章删除后应随之消失的对象地址：封面 + 正文引用的图，重复的（封面又被贴进正文）只留一次。
     * 正文没有图片语法、或语法残缺解析不出地址，都只当没有对象要回收，不报错——删文章是主流程，回收是旁路
     */
    public static List<String> referencedImages(String cover, String content) {
        Set<String> urls = new LinkedHashSet<>();
        if (cover != null && !cover.isBlank()) {
            urls.add(cover.trim());
        }
        if (content != null) {
            Matcher matcher = MARKDOWN_IMAGE.matcher(content);
            while (matcher.find()) {
                String url = matcher.group(1).trim();
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
        }
        return List.copyOf(urls);
    }

    /**
     * 把回收排到当前事务的提交点之后。回滚了却已经把对象删掉，等于删掉一篇还在的文章的图——
     * 这是这条链路唯一不可接受的错法，所以删除只能排在提交之后（§3.3 宁可漏删、不可误删）
     *
     * @param targets 待回收的完整访问地址
     * @param worker  真正执行回收的那个异步方法，由调用方以注入进来的代理引用传入（{@code imageReclaimService::reclaim}）：
     *                {@code @Async} 靠代理生效，同类内部自调用会退化成同步执行，那就白排了
     */
    public static void afterCommit(List<String> targets, Consumer<List<String>> worker) {
        if (targets.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 不在事务里就没有提交点可等，写库已经生效，直接回收
            worker.accept(targets);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.accept(targets);
            }
        });
    }

    /**
     * 异步删除属于本桶的对象。地址必须由调用方在**请求线程**上取好传进来（§2.11 那条约束在这里同样成立：
     * 换了线程既没有请求上下文，也读不到用户与文章）
     */
    @Async
    public void reclaim(List<String> urls) {
        for (String url : urls) {
            String objectKey = objectKeyOf(url);
            if (objectKey == null) {
                continue;
            }
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectKey)
                        .build());
            } catch (Exception e) {
                // 与 visit_logs 写失败同一条口径（§2.11）：只留一条 error，绝不影响主流程
                log.error("图片对象回收失败：{} 原因={}", url, e.getMessage(), e);
            }
        }
    }

    /**
     * 由完整访问地址还原对象键：只有 {@code endpoint/bucket/} 打头的地址才归本桶管，
     * 与 {@code FileService.upload} 拼 URL 用的是同一份配置，所以换 endpoint 不会让判据失配。
     * 空值、第三方图床（存量头像里的 dicebear）、随发布的默认头像（前端相对路径 {@code /default-avatar.svg}）
     * 都在这一步出局
     */
    private String objectKeyOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String prefix = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/";
        String key = url.trim();
        if (!key.startsWith(prefix)) {
            return null;
        }
        key = key.substring(prefix.length());
        return key.isBlank() ? null : key;
    }
}
