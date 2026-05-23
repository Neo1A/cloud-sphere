package com.cloudsphere.netdisk.common.aspect;

import com.cloudsphere.netdisk.common.annotation.RequiresRole;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.UserContext;
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
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 🚀 核心修正：直接传入 UNAUTHORIZED
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        String requiredRole = requiresRole.value();
        if (!requiredRole.equalsIgnoreCase(user.getRole())) {
            log.warn("【纵向越权硬阻断】用户 ID:[{}] 企图越权盗刷 [{}] 级别接口", userId, requiredRole);

            // 🚀 核心修正：直接传入 FORBIDDEN
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}