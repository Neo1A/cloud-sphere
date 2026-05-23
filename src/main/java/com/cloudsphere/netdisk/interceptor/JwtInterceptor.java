package com.cloudsphere.netdisk.interceptor;

import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.JwtUtils;
import com.cloudsphere.netdisk.common.utils.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    /**
     * 前置拦截：在请求到达 Controller 之前执行身份鉴权
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1. 尝试从 Authorization Header 中提取
        String authHeader = request.getHeader("Authorization");
        String token;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // 2. 🟢 核心升级：如果 Header 无效，尝试从 URL Query 参数 ?token=xxx 中获取
            // 这对浏览器原生的 <video>, <img>, <audio> 标签的 src 加载至关重要
            token = request.getParameter("token");
        }

        // 3. 安全判空
        if (token == null || token.isEmpty()) {
            log.warn("请求未携带有效 Token，路径: {}", request.getRequestURI());
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        try {
            // 4. 执行验签
            Claims claims = jwtUtils.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());

            // 5. 绑定上下文
            UserContext.setUserId(userId);
            return true;

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token 校验失败，异常: {}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    /**
     * 最终清理：请求完全结束（渲染完成后）触发
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        // 必须规范：强制清空当前线程的 ThreadLocal 数据
        // 在 JDK 26 虚拟线程或 Tomcat 线程池复用模型下，不清理会导致严重的内存泄漏与身份数据串流故障！
        UserContext.clear();
    }
}