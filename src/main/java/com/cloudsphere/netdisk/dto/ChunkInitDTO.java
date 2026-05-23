package com.cloudsphere.netdisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 1. 分片上传前置初始化/续传探测 DTO
 */
@Data
public class ChunkInitDTO {
    @NotBlank(message = "文件全局哈希不能为空")
    private String identifier; // 整个大文件的 SHA-256/MD5 哈希值

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "总分片数不能为空")
    private Integer totalChunks;

    @NotNull(message = "总文件大小不能为空")
    private Long totalSize;
}


