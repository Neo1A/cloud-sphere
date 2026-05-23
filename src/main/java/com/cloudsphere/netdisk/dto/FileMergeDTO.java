package com.cloudsphere.netdisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileMergeDTO {
    @NotBlank(message = "文件全局哈希不能为空")
    private String identifier;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "目标父级目录ID不能为空")
    private Long parentId;
}