package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.dto.ChunkInitDTO;
import com.cloudsphere.netdisk.dto.FileMergeDTO;
import com.cloudsphere.netdisk.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 🚀 极光网盘分片上传控制器
 * 采用 RESTful 设计，承接大文件切片上传、断点续传及合并流控。
 */
@Slf4j
@RestController
@RequestMapping("/chunk")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    /**
     * 1. 初始化分片上传会话
     * 校验上传参数合法性，构建分布式状态机，返回当前已就绪的分片索引集（支持续传探测）
     */
    @PostMapping("/init")
    public ApiResponse<Set<Integer>> init(@Validated @RequestBody ChunkInitDTO dto) {
        return ApiResponse.success(chunkUploadService.initChunkUpload(dto));
    }

    /**
     * 2. 分片并行接收
     * 接收前端切分的分片数据，直接投递给虚拟线程处理，实现真正的磁盘 I/O 高并发写入。
     */
    @PostMapping("/upload")
    public ApiResponse<Void> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("identifier") String identifier,
            // 🚀 核心优化：将 required 设为 false，允许流量安全进入 Controller 方法体内部
            @RequestParam(value = "chunkNumber", required = false) Integer chunkNumber) {

        // 🎯 【打靶修复】在应用层前置防御防空检查，优雅转换为受控业务异常
        if (chunkNumber == null) {
            log.warn("【分片上传异常】物理客户端传入的 chunkNumber 字段非法或为空白。当前标识: {}", identifier);
            throw new BusinessException(ResultCode.PARAM_ERROR, "分片序列号(chunkNumber)缺失或格式不正确，必须为有效数字");
        }

        // 顺畅流入底层 Service 执行分片落盘编排
        chunkUploadService.uploadChunk(file, identifier, chunkNumber);
        return ApiResponse.success();
    }

    /**
     * 3. 封缄合并
     * 触发分布式无锁状态机变轨，执行物理资产封缄与元数据挂载。
     */
    @PostMapping("/merge")
    public ApiResponse<Void> merge(@Validated @RequestBody FileMergeDTO dto) {
        chunkUploadService.mergeChunks(dto);
        return ApiResponse.success();
    }
}