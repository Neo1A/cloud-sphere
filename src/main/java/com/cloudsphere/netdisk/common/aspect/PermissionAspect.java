package com.cloudsphere.netdisk.common.aspect;

import com.cloudsphere.netdisk.common.annotation.RequiresRole;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.constant.UserStatusConstant; // 🚀 导入新状态常量
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.UserContextUtils;
import com.cloudsphere.netdisk.entity.User;
import com.cloudsphere.netdisk.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final UserMapper userMapper;

    @Before("@annotation(requiresRole)")
    public void doPermissionCheck(JoinPoint joinPoint, RequiresRole requiresRole) {
        Long userId = UserContextUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // ==================== 🎯 核心重构：打补丁升级状态强锁拦截 ====================
        if (user.getStatus() == null || user.getStatus() != UserStatusConstant.ENABLED) {
            log.warn("【安全熔断】冻结账户/非激活员工 ID:[{}] 工号:[{}] 企图盗刷核心业务接口，切面刚性阻断！",
                    userId, user.getUsername());
            throw new BusinessException(ResultCode.USER_DISABLED); // 触发 1004 异常
        }
        // =========================================================================

        String requiredRole = requiresRole.value();
        if (!requiredRole.equalsIgnoreCase(user.getRole())) {
            log.warn("【纵向越权硬阻断】用户 ID:[{}] 企图越权盗刷 [{}] 级别接口", userId, requiredRole);
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}