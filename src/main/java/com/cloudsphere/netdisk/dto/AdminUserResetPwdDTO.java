package com.cloudsphere.netdisk.dto;

import lombok.Data;

/**
 * 管理员手动输入重置密码 DTO
 */
@Data
public class AdminUserResetPwdDTO {
    private Integer userId;       // 目标员工ID
    private String newPassword;   // 管理员手动输入的新明文密码
}