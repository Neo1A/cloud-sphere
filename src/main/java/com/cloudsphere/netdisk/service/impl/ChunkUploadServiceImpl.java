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
     * 3. NIO 管道零拷贝物理合并（升级防重防重试版）
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

            // 1. 物理引用计数加 1
            existPhysicalFile.setRefCount(existPhysicalFile.getRefCount() + 1);
            fileInfoMapper.updateById(existPhysicalFile);

            // 2. 挂载逻辑文件树
            insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), existPhysicalFile.getId());

            // 3. 顺手清理临时温区
            Path chunkDir = Paths.get(chunkTempRoot).resolve(identifier);
            clearTempChunksAsync(chunkDir, identifier);
            return;
        }

        // --- 以下为正常的物理首次合并流程 ---
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

        try (RandomAccessFile resultFile = new RandomAccessFile(targetPath.toFile(), "rw");
             FileChannel resultChannel = resultFile.getChannel()) {

            long currentPosition = 0;
            for (File chunk : sortedChunks) {
                try (RandomAccessFile srcFile = new RandomAccessFile(chunk, "r");
                     FileChannel srcChannel = srcFile.getChannel()) {
                    long bytesTransferred = srcChannel.transferTo(0, srcChannel.size(), resultChannel);
                    currentPosition += bytesTransferred;
                    resultChannel.position(currentPosition);
                }
            }
            log.info("【NIO 零拷贝】首次大文件物理合并完成: {}", targetPath.toAbsolutePath());

            com.cloudsphere.netdisk.entity.FileInfo physicalFile = new com.cloudsphere.netdisk.entity.FileInfo();
            physicalFile.setFileIdentifier(identifier);
            physicalFile.setFilePath(targetPath.toString());
            physicalFile.setFileSize(Files.size(targetPath));
            physicalFile.setFileSuffix(suffix);
            physicalFile.setRefCount(1);
            physicalFile.setCreateTime(LocalDateTime.now());
            fileInfoMapper.insert(physicalFile);

            insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), physicalFile.getId());

            clearTempChunksAsync(chunkDir, identifier);

        } catch (IOException e) {
            log.error("大文件合并致命 IO 异常", e);
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