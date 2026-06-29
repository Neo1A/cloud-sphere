package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.api.CommonResult;
import com.cloudsphere.netdisk.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 🔧 调试工具接口（生产环境需禁用）
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final JwtUtils jwtUtils;

    /**
     * 生成永久调试 Token
     *
     * @param userId 用户ID（默认1）
     * @param username 用户名（默认admin）
     * @param deptId 部门ID（可选）
     * @return 永久有效的 Token
     */
    @GetMapping("/generate-permanent-token")
    public CommonResult<Map<String, Object>> generatePermanentToken(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "admin") String username,
            @RequestParam(required = false) Long deptId
    ) {
        String token = deptId != null
                ? jwtUtils.generatePermanentToken(userId, username, deptId)
                : jwtUtils.generatePermanentToken(userId, username);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", userId);
        result.put("username", username);
        result.put("deptId", deptId);
        result.put("expiresIn", "10年");

        return CommonResult.success(result);
    }
}