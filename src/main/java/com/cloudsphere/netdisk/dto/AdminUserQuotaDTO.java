package com.cloudsphere.netdisk.dto;

import lombok.Data;

/**
 * 管理员调整员工云盘容量配额 DTO
 */
@Data
public class AdminUserQuotaDTO {
    private Integer userId;       // 目标员工ID
    private Long totalQuota;      // 新分配的总容量配额（单位：字节 Byte）
}