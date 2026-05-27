package com.cloudsphere.netdisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 🚀 规范化重构补丁：大文件分片封缄合并数据传输对象（补全协作空间行政外键）
 */
@Data
public class FileMergeDTO {

    @NotBlank(message = "文件唯一哈希标识不能为空")
    private String identifier;

    @NotBlank(message = "文件合并名称不能为空")
    private String fileName;

    @NotNull(message = "虚拟目录树树父节点ID不能为空")
    private Long parentId;

    @NotNull(message = "预期合并分片总数不能为空")
    private Integer totalChunks;

    // ==================== 🎯 核心重构补全：承接协作公盘核心强外键 ====================

    /**
     * 冗余所述的组织架构科室 ID（解决调岗非法越权漂移）
     */
    @NotNull(message = "所属组织架构科室外键不能为空")
    private Long deptId;

    /**
     * 归属的协作存储库空间主键 ID
     */
    @NotNull(message = "物理归属存储库空间外键不能为空")
    private Long repoId;
}