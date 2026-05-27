package com.cloudsphere.netdisk.common.utils;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🚀 规范化重构：专职负责物理暂存区创建、异步虚拟线程彻底粉碎回收的生命周期清理工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadTempCleanUtil {

    private final StringRedisTemplate redisTemplate;
    private final ExecutorService cleanExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdown() {
        if (cleanExecutor != null) {
            log.info("【极光内核关停】正在安全注销临时文件清理工具，等待虚拟线程池任务收口...");
            cleanExecutor.close();
            log.info("【极光内核关停】临时文件清理工具注销完毕。");
        }
    }

    public void clearTempChunksAsync(Path chunkDir, String redisKey) {
        cleanExecutor.submit(() -> {
            try {
                redisTemplate.delete(redisKey);
                if (Files.exists(chunkDir)) {
                    Files.walk(chunkDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
                log.info("🟢【清理工具异步扫尾】临时暂存分片目录 [{}] 物理粉碎完成", chunkDir.getFileName());
            } catch (IOException e) {
                log.error("清道夫擦除分片临时脏目录失败", e);
            }
        });
    }
}