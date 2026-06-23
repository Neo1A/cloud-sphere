package com.cloudsphere.netdisk.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudsphere.netdisk.dto.AdminUserQueryDTO;
import com.cloudsphere.netdisk.dto.AdminUserQuotaDTO;
import com.cloudsphere.netdisk.dto.AdminUserResetPwdDTO;
import com.cloudsphere.netdisk.dto.AdminUserUpdateDTO;
import com.cloudsphere.netdisk.entity.User;

/**
 * 后台管理员用户管理专用域服务接口
 */
public interface AdminUserService {

    /**
     * 1.1 动态多条件弹性筛选（分页）
     */
    Page<User> pageUsers(AdminUserQueryDTO queryDTO);

    /**
     * 1.4 调整员工科室部门及系统角色岗位
     */
    void updateUserProfile(AdminUserUpdateDTO updateDTO);

    /**
     * 1.2 管理员手动建入明文重置密码
     */
    void resetPassword(AdminUserResetPwdDTO resetPwdDTO);

    /**
     * 1.3 账户双轨删除机制
     */
    void deleteUser(Integer userId, String deleteType, Long operatorId);

    /**
     * 1.5 动态调整员工个人的云盘总容量配额
     */
    void updateUserQuota(AdminUserQuotaDTO quotaDTO);
}