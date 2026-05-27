package com.cloudsphere.netdisk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    // 🚀 核心重构：主键升级为标准对象包装类 Integer（或 Long，视你的主键规划而定）
    private Integer id;
    private String username;
    private String password;
    private String realName;

    // 🚀 核心重构：彻底解决 dept_id 变 0 的罪魁祸首！升级为 Long
    private Long deptId;

    private String role;
    private Integer status; // 原本就是 Integer，继续保持
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}