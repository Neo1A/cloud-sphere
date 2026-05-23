package com.cloudsphere.netdisk.service;

import com.cloudsphere.netdisk.dto.ChunkInitDTO;
import com.cloudsphere.netdisk.dto.FileMergeDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.Set;

public interface ChunkUploadService {

    /**
     * 初始化分片上传：返回已上传的分片编号集合（断点续传核心）
     */
    Set<Integer> initChunkUpload(ChunkInitDTO dto);

    /**
     * 接收并存储单个物理分片实体
     */
    void uploadChunk(MultipartFile file, String identifier, Integer chunkNumber);

    /**
     * 触发多线程零拷贝追加合并，并注册进 MySQL 树
     */
    void mergeChunks(FileMergeDTO dto);
}