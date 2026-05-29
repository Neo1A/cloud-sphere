package com.cloudsphere.netdisk.common.aspect;

import com.cloudsphere.netdisk.common.annotation.RateLimit;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.IpUtils;
import com.cloudsphere.netdisk.common.utils.UserContextUtils;
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

        // 🚀 核心修正：优先从线程上下文中捞取已穿透代理的真实 IP，若为空（如登录、注册等免鉴权白名单接口）则通过 IpUtils 现场穿透抓取
        String ip = UserContextUtils.getCurrentIp();
        if ("unknown".equals(ip) || ip == null) {
            ip = IpUtils.getIpAddr(request);
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 2. 组装全局唯一 Redis 限流 Key: 前缀 + 接口名 + 真实物理客户端 IP
        String redisKey = rateLimit.key() + method.getName() + ":" + ip;

        // 3. 调用 Redis 执行原子累加
        Long currentCount = stringRedisTemplate.opsForValue().increment(redisKey);

        if (currentCount != null && currentCount == 1) {
            // 如果是第一次访问，为该键注入生命周期窗口
            stringRedisTemplate.expire(redisKey, rateLimit.time(), TimeUnit.SECONDS);
        }

        if (currentCount != null && currentCount > rateLimit.count()) {
            // 🎯 核心修正：打印准确的高危暴破风控控制台日志（此时 IP 为真实外网物理 IP）
            log.warn("【限流阀熔断触发】真实客户端 IP [{}] 正在恶意轰炸接口 [{}], 触发限流阈值, 计数器当前值: {}",
                    ip, method.getName(), currentCount);

            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
    }
}