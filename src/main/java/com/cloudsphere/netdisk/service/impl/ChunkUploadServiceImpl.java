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

    // 🎯 核心整流 1：前置对齐前端 500KB 的精细刚性分片特征尺寸，用于精准计算多线程文件物理偏移量 (Offset)
    private static final long FRONTIER_CHUNK_SIZE = 512000L;

    // 🎯 核心整流 2：构建专属于极光大文件多线程物理合并的专用高吞吐线程池
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private final ExecutorService mergeExecutor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            CORE_POOL_SIZE * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用方线程兜底执行，绝对不丢失写盘流
    );

    @Override
    public Set<Integer> initChunkUpload(ChunkInitDTO dto) {
        String redisKey = REDIS_CHUNK_KEY_PREFIX + dto.getIdentifier();
        Set<String> uploadedMembers = redisTemplate.opsForSet().members(redisKey);
        if (uploadedMembers != null && !uploadedMembers.isEmpty()) {
            log.info("【断点续传】发现大文件哈希 [{}] 存在历史分片，数量: {}", dto.getIdentifier(), uploadedMembers.size());
            return uploadedMembers.stream().map(Integer::parseInt).collect(Collectors.toSet());
        }
        try {
            Files.createDirectories(Paths.get(chunkTempRoot).resolve(dto.getIdentifier()));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        return Collections.emptySet();
    }

    @Override
    public void uploadChunk(MultipartFile file, String identifier, Integer chunkNumber) {
        if (file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        Path chunkPath = Paths.get(chunkTempRoot).resolve(identifier).resolve(String.valueOf(chunkNumber));
        try {
            file.transferTo(chunkPath.toFile());
            String redisKey = REDIS_CHUNK_KEY_PREFIX + identifier;
            redisTemplate.opsForSet().add(redisKey, String.valueOf(chunkNumber));
            log.info("分片落盘成功：哈希 {}, 第 {} 块已就绪", identifier, chunkNumber);
        } catch (IOException e) {
            log.error("分片写入玩客云失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 3. 🚀 NIO 管道多线程并发零拷贝大合并（升级高性能异步编排版）
     */
    @Override
    public void mergeChunks(FileMergeDTO dto) {
        Long userId = UserContext.getUserId();
        String identifier = dto.getIdentifier();

        // 🛡️ 核心大闸：先核验全服物理文件表，看此哈希大文件是否已经合并存在
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

        // --- 以下为高并发多线程物理合并流程 ---
        Path chunkDir = Paths.get(chunkTempRoot).resolve(identifier);
        if (!Files.exists(chunkDir)) {
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR);
        }

        File[] chunkFiles = chunkDir.toFile().listFiles();
        if (chunkFiles == null || chunkFiles.length == 0) {
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR);
        }

        List<File> sortedChunks = Arrays.stream(chunkFiles)
                .sorted(Comparator.comparingInt(f -> Integer.parseInt(f.getName())))
                .toList();

        String suffix = dto.getFileName().contains(".") ? dto.getFileName().substring(dto.getFileName().lastIndexOf(".")) : "";
        String physicalName = identifier + suffix;
        Path targetPath = Paths.get(storageRoot).resolve(physicalName);

        try {
            // 🎯 核心整流 3：刚性计算文件总尺寸，执行高空预分配磁盘空间，规避高并发扩容锁
            long totalSize = 0;
            for (File chunk : sortedChunks) {
                totalSize += chunk.length();
            }
            Files.createDirectories(targetPath.getParent());
            try (RandomAccessFile preAllocatedFile = new RandomAccessFile(targetPath.toFile(), "rw")) {
                preAllocatedFile.setLength(totalSize); // 划定物理连续扇区
            }

            // 🎯 核心整流 4：将传统的串行单线程 for 循环重构为 CompletableFuture 多线程并行消费者模型
            List<CompletableFuture<Void>> mergeTasks = sortedChunks.stream().map(chunk ->
                    CompletableFuture.runAsync(() -> {
                        int chunkNumber = Integer.parseInt(chunk.getName());
                        // 根据当前分片序号，刚性推导其在最终物理资产中的绝对偏移位置
                        long offset = (chunkNumber - 1) * FRONTIER_CHUNK_SIZE;

                        // 每个合并线程独立打开各自的通道句柄，完全规避多线程竞争同一个句柄引发的同步互斥阻断
                        try (RandomAccessFile targetRAF = new RandomAccessFile(targetPath.toFile(), "rw");
                             FileChannel targetChannel = targetRAF.getChannel();
                             RandomAccessFile srcRAF = new RandomAccessFile(chunk, "r");
                             FileChannel srcChannel = srcRAF.getChannel()) {

                            // 独立操纵当前线程的写指针偏移量，执行内核级 NIO 零拷贝转移
                            targetChannel.position(offset);
                            srcChannel.transferTo(0, srcChannel.size(), targetChannel);
                        } catch (IOException e) {
                            log.error("【多线程物理合并错误】分片 {} 写入失败", chunkNumber, e);
                            throw new CompletionException(e);
                        }
                    }, mergeExecutor)
            ).toList();

            // 🎯 核心整流 5：挂载同步栅栏，阻塞等待全线并发切片刷盘任务平稳闭环
            CompletableFuture.allOf(mergeTasks.toArray(new CompletableFuture[0])).join();
            log.info("【NIO 多线程并发合并】首次大文件高并发合并成功: {}", targetPath.toAbsolutePath());

            // 4. 元数据入库与多租户虚拟树挂载
            com.cloudsphere.netdisk.entity.FileInfo physicalFile = new com.cloudsphere.netdisk.entity.FileInfo();
            physicalFile.setFileIdentifier(identifier);
            physicalFile.setFilePath(targetPath.toString());
            physicalFile.setFileSize(Files.size(targetPath));
            physicalFile.setFileSuffix(suffix);
            physicalFile.setRefCount(1);
            physicalFile.setCreateTime(LocalDateTime.now());
            fileInfoMapper.insert(physicalFile);

            insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), physicalFile.getId());

            // 异步擦除扫尾
            clearTempChunksAsync(chunkDir, identifier);

        } catch (Exception e) {
            log.error("大文件并发合并发生致命未知故障", e);
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR);
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

    private void clearTempChunksAsync(Path chunkDir, String identifier) {
        try {
            redisTemplate.delete(REDIS_CHUNK_KEY_PREFIX + identifier);
            if (Files.exists(chunkDir)) {
                Files.walk(chunkDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
            log.info("临时暂存分片哈希目录 [{}] 已粉碎回收", identifier);
        } catch (IOException e) {
            log.error("扫尾清理分片临时区失败", e);
        }
    }
}