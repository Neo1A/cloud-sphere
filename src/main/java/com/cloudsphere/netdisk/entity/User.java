package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private String password;

    // 🚀 新增：同步真实数据库的角色字段
    private String role;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}