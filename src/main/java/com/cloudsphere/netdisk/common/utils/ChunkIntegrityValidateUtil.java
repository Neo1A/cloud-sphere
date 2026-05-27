package com.cloudsphere.netdisk.common.utils;

import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 🚀 规范化重构：专职负责物理分片连续性与完备性检测的强契约校验工具类
 */
@Slf4j
@Component
public class ChunkIntegrityValidateUtil {

    public List<File> validateAndSort(Path chunkDir, Integer totalChunks) {
        if (!Files.exists(chunkDir)) {
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "合并失败：分片暂存区不存在，请重新初始化上传");
        }

        File[] chunkFiles = chunkDir.toFile().listFiles();
        if (chunkFiles == null || chunkFiles.length == 0) {
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "合并失败：分片暂存区为空，缺少切片数据");
        }

        List<File> sortedChunks = Arrays.stream(chunkFiles)
                .filter(f -> f.getName().matches("\\d+"))
                .sorted(Comparator.comparingInt(f -> Integer.parseInt(f.getName())))
                .toList();

        if (sortedChunks.isEmpty() || sortedChunks.size() != totalChunks) {
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "物理切片收集不完整，期待总片数: " + totalChunks);
        }

        int firstChunkNum = Integer.parseInt(sortedChunks.getFirst().getName());
        for (int i = 0; i < sortedChunks.size(); i++) {
            int actualChunkNum = Integer.parseInt(sortedChunks.get(i).getName());
            if (actualChunkNum != firstChunkNum + i) {
                log.warn("🚨【分片链完备性熔断】切片链路出现非连续不完整断层");
                throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "大文件物理合并失败：分片不完整，出现编号断层");
            }
        }
        return sortedChunks;
    }
}