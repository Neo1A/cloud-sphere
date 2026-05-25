package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
// 🎯 核心更正：刚性对齐你实际的 MySQL 表名，移除原先错误的 "cs_file_share"
@TableName("file_share")
public class FileShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long userFileId;
    private String shortLink;
    private String extractionCode;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}