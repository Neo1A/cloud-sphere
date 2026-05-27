package com.cloudsphere.netdisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 🚀 规范化重构补丁：分片上传初始化数据传输对象（补全协作空间行政外键）
 */
@Data
public class ChunkInitDTO {

    @NotBlank(message = "文件唯一哈希标识不能为空")
    private String identifier;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件总大小不能为空")
    private Long fileSize;

    @NotNull(message = "分片总块数不能为空")
    private Integer totalChunks;

    // ==================== 🎯 核心重构补全：承接协作公盘核心强外键 ====================

    /**
     * 所属的部门/科室ID（对应组织架构主键）
     */
    @NotNull(message = "所属科室部门外键不能为空")
    private Long deptId;

    /**
     * 归属的存储库物理空间ID（对应存储库主表）
     */
    @NotNull(message = "目标存储库空间外键不能为空")
    private Long repoId;
}