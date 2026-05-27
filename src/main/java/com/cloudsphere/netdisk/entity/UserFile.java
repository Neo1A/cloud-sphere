package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 🚀 核心重构补丁：补全企业协作空间行政科层外键（NOT NULL 约束对齐）
 */
@Data
@TableName("user_file")
public class UserFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    // 🚀 核心补全：归属的高能协作存储空间主键 ID（对应 storage_repository 表）
    private Long repoId;

    private Long fileInfoId;
    private String fileName;
    private Long parentId;

    @TableField("is_dir")
    private Boolean isDir;

    // 🚀 核心补全：冗余所属的组织架构科室 ID（解决调岗非法越权漂移）
    private Long deptId;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}