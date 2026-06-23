package com.cloudsphere.netdisk.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudsphere.netdisk.common.annotation.RequiresRole;
import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.common.utils.UserContextUtils;
import com.cloudsphere.netdisk.dto.AdminUserQueryDTO;
import com.cloudsphere.netdisk.dto.AdminUserQuotaDTO;
import com.cloudsphere.netdisk.dto.AdminUserResetPwdDTO;
import com.cloudsphere.netdisk.dto.AdminUserUpdateDTO;
import com.cloudsphere.netdisk.entity.User;
import com.cloudsphere.netdisk.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 端点 1.1：用户多条件弹性检索列表
     */
    @GetMapping("/page")
    @RequiresRole("ADMIN")
    public ApiResponse<Page<User>> pageUsers(AdminUserQueryDTO queryDTO) {
        return ApiResponse.success(adminUserService.pageUsers(queryDTO));
    }

    /**
     * 端点 1.4：员工科室/行政岗位调薪调岗修改
     */
    @PutMapping("/profile")
    @RequiresRole("ADMIN")
    public ApiResponse<Void> updateUserProfile(@RequestBody AdminUserUpdateDTO updateDTO) {
        adminUserService.updateUserProfile(updateDTO);
        return ApiResponse.success();
    }

    /**
     * 端点 1.2：管理员手动录入新密码强制重置
     */
    @PutMapping("/password/reset")
    @RequiresRole("ADMIN")
    public ApiResponse<Void> resetPassword(@RequestBody AdminUserResetPwdDTO resetPwdDTO) {
        adminUserService.resetPassword(resetPwdDTO);
        return ApiResponse.success();
    }

    /**
     * 端点 1.3：双轨删除控制台
     * deleteType 传 "logical" 为状态禁用逻辑删，传 "physical" 为物理擦除
     */
    @DeleteMapping("/{userId}")
    @RequiresRole("ADMIN")
    public ApiResponse<Void> deleteUser(
            @PathVariable Integer userId,
            @RequestParam(value = "type", defaultValue = "logical") String deleteType) {

        Long operatorId = UserContextUtils.getUserId();
        adminUserService.deleteUser(userId, deleteType, operatorId);
        return ApiResponse.success();
    }

    /**
     * 端点 1.5：管理员调整指定员工的云盘最大容量配额限制
     */
    @PutMapping("/quota")
    @com.cloudsphere.netdisk.common.annotation.RequiresRole("ADMIN")
    public ApiResponse<Void> updateUserQuota(@RequestBody AdminUserQuotaDTO quotaDTO) {
        adminUserService.updateUserQuota(quotaDTO);
        return ApiResponse.success();
    }
}