package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_file")
public class UserFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fileInfoId;
    private String fileName;
    private Long parentId;

    @TableField("is_dir")
    private Boolean isDir;

    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}