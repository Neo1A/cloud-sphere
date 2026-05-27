package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.UserContext;
import com.cloudsphere.netdisk.dto.ChunkInitDTO;
import com.cloudsphere.netdisk.dto.FileMergeDTO;
import com.cloudsphere.netdisk.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private final StringRedisTemplate redisTemplate;
    private final com.cloudsphere.netdisk.mapper.UserFileMapper userFileMapper;
    private final com.cloudsphere.netdisk.mapper.FileInfoMapper fileInfoMapper;

    @Value("${cloudsphere.upload.storage-path}")
    private String storageRoot;

    @Value("${cloudsphere.upload.temp-path}")
    private String chunkTempRoot;

    private static final String REDIS_CHUNK_KEY_PREFIX = "cloudsphere:upload:chunks:";

    // 🚀 活化线程池：专门用于高性能异步擦除扫尾，绝对不占用主干大文件传输管道的一毫秒阻塞开销
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private final ExecutorService cleanExecutor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            CORE_POOL_SIZE * 2,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * Step 1: 彻底根治断点续传下的“温区滑落”漏洞
     */
    @Override
    public Set<Integer> initChunkUpload(ChunkInitDTO dto) {
        String redisKey = REDIS_CHUNK_KEY_PREFIX + dto.getIdentifier();

        // 🛡️ 刚性整流 1：不论是否命中续传缓存，无条件确保物理磁盘上的临时哈希温区目录 100% 同步存在！
        try {
            Files.createDirectories(Paths.get(chunkTempRoot).resolve(dto.getIdentifier()));
        } catch (IOException e) {
            log.error("【分片初始化】构筑临时接收温区失败, 哈希: {}", dto.getIdentifier(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        Set<String> uploadedMembers = redisTemplate.opsForSet().members(redisKey);
        if (uploadedMembers != null && !uploadedMembers.isEmpty()) {
            log.info("【断点续传】发现大文件哈希 [{}] 存在历史分片，数量: {}", dto.getIdentifier(), uploadedMembers.size());
            return uploadedMembers.stream().map(Integer::parseInt).collect(Collectors.toSet());
        }

        return Collections.emptySet();
    }

    /**
     * Step 2: 捍卫多线程并发上传！注入内核级线程安全防御大闸
     */
    @Override
    public void uploadChunk(MultipartFile file, String identifier, Integer chunkNumber) {
        if (file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        Path chunkDir = Paths.get(chunkTempRoot).resolve(identifier);
        Path chunkPath = chunkDir.resolve(String.valueOf(chunkNumber));

        try {
            // 🛡️ 刚性整流 2：在高并发多线程分片落盘前，执行原子化路径生命探测
            // 确保多线程交错轰炸时，哪怕温区由于特殊外界因素产生任何滑落，也能瞬间线程安全地原位修复
            if (!Files.exists(chunkDir)) {
                Files.createDirectories(chunkDir);
            }

            // 抛入对应分片槽位
            file.transferTo(chunkPath.toFile());

            String redisKey = REDIS_CHUNK_KEY_PREFIX + identifier;
            redisTemplate.opsForSet().add(redisKey, String.valueOf(chunkNumber));
            log.debug("分片落盘成功：哈希 {}, 第 {} 块已就绪", identifier, chunkNumber);
        } catch (IOException e) {
            log.error("❌【分片高并发写入失败】哈希: {}, 分片序号: {}, 原因: {}", identifier, chunkNumber, e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "后端物理分片流并发写盘失败");
        }
    }

    /**
     * Step 3 & 4: 降维细粒度锁 + 完备性无损断层扫描机制
     */
    @Override
    public void mergeChunks(FileMergeDTO dto) {
        Long userId = UserContext.getUserId();
        String identifier = dto.getIdentifier();

        // 1. 🛡️ 秒传大闸：先核验全服物理文件表
        com.cloudsphere.netdisk.entity.FileInfo existPhysicalFile = fileInfoMapper.selectOne(
                new LambdaQueryWrapper<com.cloudsphere.netdisk.entity.FileInfo>()
                        .eq(com.cloudsphere.netdisk.entity.FileInfo::getFileIdentifier, identifier)
        );

        if (existPhysicalFile != null) {
            log.info("【秒传大闸触发】哈希 [{}] 物理实体已存在，跳过磁盘合并，直接进行多租户软链挂载", identifier);
            existPhysicalFile.setRefCount(existPhysicalFile.getRefCount() + 1);
            fileInfoMapper.updateById(existPhysicalFile);
            insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), existPhysicalFile.getId());
            Path chunkDir = Paths.get(chunkTempRoot).resolve(identifier);
            clearTempChunksAsync(chunkDir, identifier);
            return;
        }

        // 🛡️ 刚性整流 3：取消原先大粒度的方法 synchronized 锁，改用 JVM 级字符串常量池特征细粒度分段锁。
        // 这能使得并发环境下，合并不同文件的用户互不干扰，合并相同文件的幽灵请求原地挂起，完美对冲死锁排队
        synchronized (identifier.intern()) {

            // 二次检索，防止高并发下重复点击引起的并发穿透
            com.cloudsphere.netdisk.entity.FileInfo doubleCheckFile = fileInfoMapper.selectOne(
                    new LambdaQueryWrapper<com.cloudsphere.netdisk.entity.FileInfo>()
                            .eq(com.cloudsphere.netdisk.entity.FileInfo::getFileIdentifier, identifier)
            );
            if (doubleCheckFile != null) {
                insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), doubleCheckFile.getId());
                return;
            }

            Path chunkDir = Paths.get(chunkTempRoot).resolve(identifier);
            if (!Files.exists(chunkDir)) {
                throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "合并失败：分片暂存温区已被擦除");
            }

            File[] chunkFiles = chunkDir.toFile().listFiles();
            if (chunkFiles == null || chunkFiles.length == 0) {
                throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "合并失败：未探测到有效的物理切片资产");
            }

            // 精准排序：过滤非数字杂质，严格按照分片编号自增升序排列
            List<File> sortedChunks = Arrays.stream(chunkFiles)
                    .filter(f -> f.getName().matches("\\d+"))
                    .sorted(Comparator.comparingInt(f -> Integer.parseInt(f.getName())))
                    .toList();

            // 🛡️ 刚性整流 4：分片链完备性断层安全检测大闸！
            // 严格对齐物理索引，如果排好序的切片出现编号断层（例如 1, 2, 4 缺失了 3），直接熔断报错，严防受损大文件入库
            for (int i = 0; i < sortedChunks.size(); i++) {
                int expectedChunkNum = i + 1;
                int actualChunkNum = Integer.parseInt(sortedChunks.get(i).getName());
                if (actualChunkNum != expectedChunkNum) {
                    log.warn("🚨【分片断层熔断】大文件 [{}] 合并中止，检测到核心分片缺失。期待第 {} 片，实际为第 {} 片",
                            dto.getFileName(), expectedChunkNum, actualChunkNum);
                    throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "大文件物理合并失败：检测到分片不完整，缺少第 " + expectedChunkNum + " 片");
                }
            }

            // 构筑最终物理落盘路径
            String suffix = dto.getFileName().contains(".") ? dto.getFileName().substring(dto.getFileName().lastIndexOf(".")) : "";
            String physicalName = identifier + suffix;
            Path targetPath = Paths.get(storageRoot).resolve(physicalName);

            try {
                Files.createDirectories(targetPath.getParent());

                // 全局只打开一个目标文件的通道句柄，顺序追加写入，天然免疫分片尺寸自适应变化
                try (RandomAccessFile targetRAF = new RandomAccessFile(targetPath.toFile(), "rw");
                     FileChannel targetChannel = targetRAF.getChannel()) {

                    targetChannel.position(0);
                    for (File chunk : sortedChunks) {
                        try (RandomAccessFile srcRAF = new RandomAccessFile(chunk, "r");
                             FileChannel srcChannel = srcRAF.getChannel()) {
                            srcChannel.transferTo(0, srcChannel.size(), targetChannel);
                        }
                    }
                }

                log.info("【NIO 顺序追加合并成功】大文件已成功安全落盘: {}", targetPath.toAbsolutePath());

                // 元数据入库与多租户虚拟树逻辑挂载
                com.cloudsphere.netdisk.entity.FileInfo physicalFile = new com.cloudsphere.netdisk.entity.FileInfo();
                physicalFile.setFileIdentifier(identifier);
                physicalFile.setFilePath(targetPath.toString());
                physicalFile.setFileSize(Files.size(targetPath));
                physicalFile.setFileSuffix(suffix);
                physicalFile.setRefCount(1);
                physicalFile.setCreateTime(LocalDateTime.now());
                fileInfoMapper.insert(physicalFile);

                insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), physicalFile.getId());

                // 🧹 唤醒扫尾清道夫
                clearTempChunksAsync(chunkDir, identifier);

            } catch (Exception e) {
                log.error("【极光合并引擎故障】大文件顺序合并发生严重异常, 哈希: {}", identifier, e);
                try {
                    Files.deleteIfExists(targetPath);
                } catch (IOException ignored) {}
                throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "服务器执行物理资产封缄合并失败");
            }
        }
    }

    private void insertVirtualFile(Long userId, Long parentId, String fileName, Long fileInfoId) {
        com.cloudsphere.netdisk.entity.UserFile virtualFile = new com.cloudsphere.netdisk.entity.UserFile();
        virtualFile.setUserId(userId);
        virtualFile.setParentId(parentId);
        virtualFile.setFileName(fileName);
        virtualFile.setIsDir(false);
        virtualFile.setFileInfoId(fileInfoId);
        virtualFile.setDeleted(0);
        virtualFile.setCreateTime(LocalDateTime.now());
        virtualFile.setUpdateTime(LocalDateTime.now());
        userFileMapper.insert(virtualFile);
    }

    /**
     * Step 5: 刚性兑现真正的异步高性能清道夫流控
     */
    private void clearTempChunksAsync(Path chunkDir, String identifier) {
        // 🛡️ 刚性整流 5：彻底转交专门的线程池异步执行，让主线程立刻向用户回弹 HTTP 200 成功响应，彻底粉碎 I/O 拖尾带来的顿感
        cleanExecutor.submit(() -> {
            try {
                redisTemplate.delete(REDIS_CHUNK_KEY_PREFIX + identifier);
                if (Files.exists(chunkDir)) {
                    Files.walk(chunkDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
                log.info("🟢【清道夫异步扫尾】临时暂存分片哈希目录 [{}] 已粉碎回收完成", identifier);
            } catch (IOException e) {
                log.error("扫尾清理分片临时区失败", e);
            }
        });
    }
}