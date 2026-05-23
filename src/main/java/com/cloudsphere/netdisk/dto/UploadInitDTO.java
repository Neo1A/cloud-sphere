package com.cloudsphere.netdisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 极光网盘：单文件秒传/普通上传初始化请求对象
 */
@Data
public class UploadInitDTO {

    @NotBlank(message = "文件名不能为空")
    private String name;

    @NotNull(message = "父级ID不能为空")
    private Long parentId;

    @NotBlank(message = "文件哈希不能为空")
    private String sha256;

    @NotNull(message = "文件大小不能为空")
    private Long size;
}