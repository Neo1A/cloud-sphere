package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cs_file_share")
public class FileShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;             // 创建分享的用户 ID
    private Long userFileId;         // 分享的网盘逻辑虚拟文件/文件夹 ID
    private String shortLink;        // 8位唯一不重复的短链特征码
    private String extractionCode;   // 4位混淆提取码
    private LocalDateTime expireTime;// 绝对失效时间戳 (null 表示常驻有效)
    private LocalDateTime createTime;
}