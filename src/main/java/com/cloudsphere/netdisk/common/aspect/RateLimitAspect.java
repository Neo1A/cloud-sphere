package com.cloudsphere.netdisk.common.aspect;

import com.cloudsphere.netdisk.common.annotation.RateLimit;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.IpUtils; // 假设你有获取IP的工具类
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    @Before("@annotation(rateLimit)")
    public void interceptor(JoinPoint joinPoint, RateLimit rateLimit) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;
        HttpServletRequest request = attributes.getRequest();

        // 1. 获取触发接口的客户端真实 IP
        String ip = request.getRemoteAddr();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 2. 组装全局唯一 Redis 限流 Key: 前缀 + 接口名 + 访问者IP
        String redisKey = rateLimit.key() + method.getName() + ":" + ip;

        // 3. 调用玩客云 Redis 执行原子累加
        Long currentCount = stringRedisTemplate.opsForValue().increment(redisKey);

        if (currentCount != null && currentCount == 1) {
            // 如果是第一次访问，为该键注入生命周期窗口
            stringRedisTemplate.expire(redisKey, rateLimit.time(), TimeUnit.SECONDS);
        }

        if (currentCount != null && currentCount > rateLimit.count()) {
            log.warn("【限流阀熔断触发】IP [{}] 恶意轰炸接口 [{}], 计数器: {}", ip, method.getName(), currentCount);

            // 🚀 核心修正：直接把整个 TOO_MANY_REQUESTS 枚举丢进去，不拆分参数
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
    }
}