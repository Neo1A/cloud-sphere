package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 🚀 核心重构：分布式文件并发上传会话状态机实体
 */
@Data
@TableName("upload_session")
public class UploadSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对应前端的 identifier (大文件 MD5 唯一特征码)
     */
    private String uploadId;

    /**
     * 核心生命周期状态指针：UPLOADING(上传中) -> MERGING(合并中) -> DONE(已完成)
     */
    private String status;

    /**
     * 上传者/逻辑所有者 ID
     */
    private Long userId;

    /**
     * 归属的存储库ID
     */
    private Long repoId;

    /**
     * 所属的部门/科室ID
     */
    private Long deptId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}