package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.annotation.RateLimit;
import com.cloudsphere.netdisk.common.annotation.RequiresRole;
import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.common.constant.UserStatusConstant;
import com.cloudsphere.netdisk.common.utils.UserContext;
import com.cloudsphere.netdisk.dto.UserLoginDTO;
import com.cloudsphere.netdisk.dto.UserRegisterDTO; // 🚀 核心修正：导入全新的企业入职注册 DTO
import com.cloudsphere.netdisk.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 1. 企业员工入职注册（升级版）
     * 挂载限流阀：防止恶意用机器脚本疯狂注册爆破矿区存储池
     * 限制规则：60秒内同一个IP最多调用 3 次
     */
    @PostMapping("/register")
    @RateLimit(count = 3)
    public ApiResponse<Void> register(@Validated @RequestBody UserRegisterDTO dto) { // 🚀 升级为 UserRegisterDTO
        // 级联透传：工号、密码、真实姓名、所属科室ID、行政岗位角色
        userService.register(dto.getUsername(), dto.getPassword(), dto.getRealName(), dto.getDeptId(), dto.getRole().getCode());
        return ApiResponse.success();
    }

    /**
     * 2. 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<String> login(@Validated @RequestBody UserLoginDTO dto) {
        return ApiResponse.success(userService.login(dto.getUsername(), dto.getPassword()));
    }

    /**
     * 3. 挂载权限防护大闸：只有系统最高管理员（ADMIN）才能查阅全矿核心监控 reports
     */
    @GetMapping("/admin/monitor")
    @RequiresRole("ADMIN")
    public ApiResponse<String> systemMonitor() {
        return ApiResponse.success("【极光网盘内核报告】全矿数字化仓储存储池状态健康，底层分布式存储引擎读写完美。");
    }

    /**
     * 4. 禁用/冻结企业员工账户
     * 权限安全防护闸：唯有系统最高超级管理员（ADMIN）准许封禁员工通行证
     */
    @PostMapping("/admin/disable/{id}")
    @RequiresRole("ADMIN")
    public ApiResponse<Void> disableUser(@PathVariable Long id) {
        // 从当前绑定的安全无状态拦截上下文中，安全提取执行该行为的管理员 ID
        Long operatorId = UserContext.getUserId();

        // 级联下发给 Service 层：目标 ID、禁用状态(0)、操作人 ID
        userService.updateUserStatus(id, UserStatusConstant.DISABLED, operatorId);
        return ApiResponse.success();
    }

    /**
     * 5. 启用/解冻企业员工账户
     * 权限安全防护闸：唯有系统最高超级管理员（ADMIN）准许修复解冻员工通行证
     */
    @PostMapping("/admin/enable/{id}")
    @RequiresRole("ADMIN")
    public ApiResponse<Void> enableUser(@PathVariable Long id) {
        Long operatorId = UserContext.getUserId();

        // 级联下发给 Service 层：目标 ID、启用状态(1)、操作人 ID
        userService.updateUserStatus(id, UserStatusConstant.ENABLED, operatorId);
        return ApiResponse.success();
    }

}