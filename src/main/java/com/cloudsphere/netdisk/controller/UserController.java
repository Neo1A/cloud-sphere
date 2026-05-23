package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.annotation.RateLimit;
import com.cloudsphere.netdisk.common.annotation.RequiresRole;
import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.dto.UserLoginDTO; // 🚀 核心修正：精准导入你现有的 DTO
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
     * 1. 挂载限流阀：防止恶意用机器脚本疯狂注册爆破玩客云磁盘
     * 限制规则：60秒内同一个IP最多调用 3 次
     */
    @PostMapping("/register")
    @RateLimit(count = 3)
    public ApiResponse<Void> register(@Validated @RequestBody UserLoginDTO dto) { // 🚀 修正为 UserLoginDTO
        userService.register(dto.getUsername(), dto.getPassword());
        return ApiResponse.success();
    }

    /**
     * 2. 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<String> login(@Validated @RequestBody UserLoginDTO dto) { // 🚀 修正为 UserLoginDTO
        return ApiResponse.success(userService.login(dto.getUsername(), dto.getPassword()));
    }

    /**
     * 3. 挂载权限防护大闸：只有玩客云数据库里 role='ADMIN' 的超级管理员才能看
     */
    @GetMapping("/admin/monitor")
    @RequiresRole("ADMIN")
    public ApiResponse<String> systemMonitor() {
        return ApiResponse.success("【极光网盘内核报告】玩客云 10.1.1.100 存储池健康，磁盘读写完美。");
    }
}