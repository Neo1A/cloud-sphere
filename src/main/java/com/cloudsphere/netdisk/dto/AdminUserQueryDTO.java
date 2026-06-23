package com.cloudsphere.netdisk.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员多条件弹性筛选 DTO
 */
@Data
public class AdminUserQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer page = 1;
    private Integer pageSize = 10;

    // 多条件组合参数（支持单个、多个或完整条件传参）
    private String username;      // 工号 (模糊)
    private String realName;      // 姓名 (模糊)
    private Long deptId;          // 科室ID (精准)
    private Integer status;       // 状态 (精准)
    private String role;          // 岗位角色 (精准)
}