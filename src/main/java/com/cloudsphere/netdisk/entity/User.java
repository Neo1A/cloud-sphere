package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;          // 🛠️ 核心修正：由 Integer 升级为标准 Long，对齐数据库 bigint
    private String username;
    private String password;
    private String realName;
    private Long deptId;
    private String role;
    private Integer status;

    // 🆕 增量注入：网盘生命线——数字配额大闸字段
    private Long totalQuota;   // 个人云盘总配额(单位:字节)
    private Long usedStorage;  // 个人云盘当前已使用空间(单位:字节)

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}