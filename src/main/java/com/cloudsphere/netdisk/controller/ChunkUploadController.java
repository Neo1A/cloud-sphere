package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.dto.ChunkInitDTO;
import com.cloudsphere.netdisk.dto.FileMergeDTO;
import com.cloudsphere.netdisk.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping(value = "/file/chunk", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
@Validated
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    /**
     * Step 1: 上传初始化探测（断点续传探测门阀）
     */
    @PostMapping("/init")
    public ApiResponse<Set<Integer>> initUpload(@Validated @RequestBody ChunkInitDTO dto) {
        return ApiResponse.success(chunkUploadService.initChunkUpload(dto));
    }

    /**
     * Step 2: 物理接收单个分片
     */
    @PostMapping("/upload")
    public ApiResponse<Void> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("identifier") String identifier,
            @RequestParam("chunkNumber") Integer chunkNumber) {
        chunkUploadService.uploadChunk(file, identifier, chunkNumber);
        return ApiResponse.success();
    }

    /**
     * Step 3: 所有分片齐聚一堂，下发最终异步大合并指令
     */
    @PostMapping("/merge")
    public ApiResponse<Void> mergeChunks(@Validated @RequestBody FileMergeDTO dto) {
        chunkUploadService.mergeChunks(dto);
        return ApiResponse.success();
    }
}