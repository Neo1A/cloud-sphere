package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.*; // 全量导入带 Util 后缀的基础基础设施器官
import com.cloudsphere.netdisk.dto.ChunkInitDTO;
import com.cloudsphere.netdisk.dto.FileMergeDTO;
import com.cloudsphere.netdisk.entity.FileInfo;
import com.cloudsphere.netdisk.entity.UploadSession;
import com.cloudsphere.netdisk.entity.User;
import com.cloudsphere.netdisk.entity.UserFile;
import com.cloudsphere.netdisk.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors; // 🚀 补上这一行，消灭 Collectors 找不到符号高警

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private final StringRedisTemplate redisTemplate;
    private final com.cloudsphere.netdisk.mapper.UserFileMapper userFileMapper;
    private final com.cloudsphere.netdisk.mapper.FileInfoMapper fileInfoMapper;
    private final com.cloudsphere.netdisk.mapper.UploadSessionMapper sessionMapper;
    private final com.cloudsphere.netdisk.mapper.UserMapper userMapper;
    private final TransactionTemplate transactionTemplate;

    // 🚀 强类型装配规范化命名后的全套 Util 工具链组件
    private final ChunkIntegrityValidateUtil fileValidator;
    private final ChunkMergeEngineUtil mergeEngine;
    private final UploadSessionStateMachineUtil stateMachine;
    private final UploadTempCleanUtil tempCleaner;
    private final InstantUploadCheckUtil instantChecker;

    @Value("${cloudsphere.upload.storage-path}")
    private String storageRoot;

    @Value("${cloudsphere.upload.temp-path}")
    private String chunkTempRoot;

    private static final String REDIS_CHUNK_KEY_PREFIX = "cloudsphere:upload:chunks:";

    @Override
    public Set<Integer> initChunkUpload(ChunkInitDTO dto) {
        Long userId = UserContextUtils.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (dto.getRepoId() == null || dto.getDeptId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "初始化失败：核心存储库或所属科室外键缺失");
        }

        String identifier = dto.getIdentifier();
        try {
            Files.createDirectories(Paths.get(chunkTempRoot).resolve(identifier));
        } catch (IOException e) {
            log.error("【分片初始化】构筑临时接收温区失败，路径: {}/{}", chunkTempRoot, identifier, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        try {
            if (sessionMapper.selectOne(new LambdaQueryWrapper<UploadSession>().eq(UploadSession::getUploadId, identifier)) == null) {
                UploadSession session = new UploadSession();
                session.setUploadId(identifier);
                session.setStatus("UPLOADING");
                session.setUserId(userId);
                session.setRepoId(dto.getRepoId());
                session.setDeptId(dto.getDeptId());
                session.setCreateTime(LocalDateTime.now());
                session.setUpdateTime(LocalDateTime.now());
                sessionMapper.insert(session);
            }
        } catch (Exception e) {
            log.warn("【分片初始化】upload_session 表写入失败（可能表不存在），继续流程: {}", e.getMessage());
        }

        try {
            return Optional.ofNullable(redisTemplate.opsForSet().members(REDIS_CHUNK_KEY_PREFIX + identifier))
                    .orElse(Collections.emptySet()).stream().map(Integer::parseInt).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("【分片初始化】Redis 不可达，返回空续传集合: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    @Override
    public void uploadChunk(MultipartFile file, String identifier, Integer chunkNumber) {
        if (file.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR);
        Path chunkDir = Paths.get(chunkTempRoot).resolve(identifier);
        try {
            if (!Files.exists(chunkDir)) Files.createDirectories(chunkDir);
            file.transferTo(chunkDir.resolve(String.valueOf(chunkNumber)).toFile());

            String redisKey = REDIS_CHUNK_KEY_PREFIX + identifier;
            try {
                redisTemplate.opsForSet().add(redisKey, String.valueOf(chunkNumber));
                redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("【分片上传】Redis 不可达，跳过续传记录: {}", e.getMessage());
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "后端物理分片流并发写盘失败");
        }
    }

    @Override
    public void mergeChunks(FileMergeDTO dto) {
        Long userId = UserContextUtils.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (dto.getRepoId() == null || dto.getDeptId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "合并中断：归属公共库或所属部门外键不完整");
        }

        String identifier = dto.getIdentifier();

        // 1. 调用秒传核验 Util
        FileInfo existPhysicalFile = instantChecker.checkAndIncrement(identifier);
        if (existPhysicalFile != null) {
            checkQuota(userId, existPhysicalFile.getFileSize());
            insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), existPhysicalFile.getId(), dto.getDeptId(), dto.getRepoId());
            addUsedStorage(userId, existPhysicalFile.getFileSize());
            stateMachine.forceUpdateStatus(identifier, "DONE");
            tempCleaner.clearTempChunksAsync(Paths.get(chunkTempRoot).resolve(identifier), REDIS_CHUNK_KEY_PREFIX + identifier);
            return;
        }

        // 2. 调用会话状态机抢占合并独占特权锁
        if (!stateMachine.claimMergeLock(identifier)) return;

        // 3. 调用完备性校验 Util 扫描并排序资产【100% 事务外，免除连接霸占】
        Path chunkDir = Paths.get(chunkTempRoot).resolve(identifier);
        List<File> sortedChunks = fileValidator.validateAndSort(chunkDir, dto.getTotalChunks());

        String suffix = dto.getFileName().contains(".") ? dto.getFileName().substring(dto.getFileName().lastIndexOf(".")) : "";
        Path tempMergeFile = Paths.get(storageRoot, "temp_merge").resolve(identifier + suffix + ".tmp");
        Path[] finalShardedFileHolder = new Path[1]; // 异常物理碎尸逃生舱指针

        try {
            // 4. 调用 NIO 追加合并引擎 Util 【100% 事务外，长 I/O 与连接池彻底解耦】
            mergeEngine.merge(sortedChunks, tempMergeFile);

            // 5. 进入微秒级超短编程式事务刷盘
            transactionTemplate.execute(status -> {
                try {
                    FileInfo physicalFile = new FileInfo();
                    physicalFile.setFileIdentifier(identifier);
                    physicalFile.setFileSuffix(suffix);
                    physicalFile.setRefCount(1);
                    physicalFile.setCreateTime(LocalDateTime.now());
                    physicalFile.setFilePath("LOCKING_BY_STATUS_MACHINE");
                    physicalFile.setFileSize(0L);

                    fileInfoMapper.insert(physicalFile); // 回填物理自增 ID

                    String relativeFolder = StoragePathUtils.getHashShardedFolder(physicalFile.getId());
                    String relativeFilePath = StoragePathUtils.getFullShardedPath(physicalFile.getId(), suffix);
                    Path targetAbsoluteFile = Paths.get(storageRoot, relativeFilePath);
                    finalShardedFileHolder[0] = targetAbsoluteFile;

                    if (!Files.exists(targetAbsoluteFile.getParent())) {
                        Files.createDirectories(targetAbsoluteFile.getParent());
                    }

                    // 操作系统底层原子重命名：耗时极其轻微
                    Files.move(tempMergeFile, targetAbsoluteFile, StandardCopyOption.REPLACE_EXISTING);

                    // 锁多元数据
                    physicalFile.setFilePath(relativeFilePath);
                    long mergedFileSize = Files.size(targetAbsoluteFile);
                    physicalFile.setFileSize(mergedFileSize);
                    fileInfoMapper.updateById(physicalFile);

                    // 容量配额原子核验
                    checkQuota(userId, mergedFileSize);

                    // 多租户树挂载
                    insertVirtualFile(userId, dto.getParentId(), dto.getFileName(), physicalFile.getId(), dto.getDeptId(), dto.getRepoId());

                    // 原子累计已用空间
                    addUsedStorage(userId, mergedFileSize);

                    // 状态机闭环推进
                    stateMachine.updateStatus(identifier, "MERGING", "DONE");
                    return null;
                } catch (Exception ex) {
                    status.setRollbackOnly();
                    throw new RuntimeException(ex);
                }
            });

            // 调用异步清理 Util 执行切片清零
            tempCleaner.clearTempChunksAsync(chunkDir, REDIS_CHUNK_KEY_PREFIX + identifier);

        } catch (Exception e) {
            log.error("【极光合并流控故障】文件哈希 [{}] 变缄合并失败，启动会话逆向解冻", identifier, e);
            stateMachine.rollbackStatus(identifier, "UPLOADING"); // 状态解冻释放，允许前端原地重试
            try {
                Files.deleteIfExists(tempMergeFile);
                if (finalShardedFileHolder[0] != null) Files.deleteIfExists(finalShardedFileHolder[0]);
            } catch (IOException ignored) {
            }
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "大文件封缄无锁合并失败，请重新尝试合并");
        }
    }

    private void insertVirtualFile(Long userId, Long parentId, String fileName, Long fileInfoId, Long deptId, Long repoId) {
        UserFile virtualFile = new UserFile();
        virtualFile.setUserId(userId);
        virtualFile.setParentId(parentId);
        virtualFile.setFileName(fileName);
        virtualFile.setIsDir(false);
        virtualFile.setFileInfoId(fileInfoId);
        virtualFile.setDeptId(deptId);
        virtualFile.setRepoId(repoId);
        virtualFile.setDeleted(0);
        virtualFile.setCreateTime(LocalDateTime.now());
        virtualFile.setUpdateTime(LocalDateTime.now());
        userFileMapper.insert(virtualFile);
    }

    private void checkQuota(Long userId, long fileSize) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Long quota = user.getTotalQuota();
        if (quota != null && quota > 0) {
            long used = user.getUsedStorage() != null ? user.getUsedStorage() : 0L;
            if (used + fileSize > quota) {
                throw new BusinessException(ResultCode.SPACE_LIMIT_EXCEEDED);
            }
        }
    }

    private void addUsedStorage(Long userId, long fileSize) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .setSql("used_storage = COALESCE(used_storage, 0) + " + fileSize)
                .eq(User::getId, userId));
    }

}