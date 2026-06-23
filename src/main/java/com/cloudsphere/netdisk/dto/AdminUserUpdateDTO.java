package com.cloudsphere.netdisk.dto;

import lombok.Data;

/**
 * 管理员调整员工信息（科室调拨、角色修改）DTO
 */
@Data
public class AdminUserUpdateDTO {
    private Integer userId;       // 目标员工ID
    private String realName;      // 修改姓名
    private Long deptId;          // 新科室ID
    private String role;          // 新行政岗位
}