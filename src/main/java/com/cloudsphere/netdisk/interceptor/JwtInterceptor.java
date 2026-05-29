package com.cloudsphere.netdisk.interceptor;

import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.JwtUtils;
import com.cloudsphere.netdisk.common.utils.UserContextUtils;
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

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        String token;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = request.getParameter("token");
        }

        if (token == null || token.isEmpty()) {
            log.warn("【安全鉴权拒绝】请求未携带 Token 凭证，阻断访问。路径: {}", request.getRequestURI());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "凭证已失效，请重新登录");
        }

        try {
            // 解析安全载荷
            Long userId = jwtUtils.getUserIdFromToken(token);
            String username = jwtUtils.getUsernameFromToken(token);
            Long deptId = jwtUtils.getDeptIdFromToken(token);

            if (userId == null || username == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "凭证载荷不完备，安全审计失败");
            }

            // 🎯 核心联动：精准捞出上一个拦截器已经初始化好的、带真实 IP 的会话对象
            UserContextUtils.UserSession session = UserContextUtils.get();
            if (session == null) {
                session = new UserContextUtils.UserSession();
            }

            // 增量修正会话内的身份数据，覆盖默认的匿名标记
            session.setUserId(userId);
            session.setUsername(username);
            session.setDeptId(deptId);

            // 重新刷新回 ThreadLocal 线程上下文
            UserContextUtils.set(session);

            return true;

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("【安全鉴权失败】Token 验签故障: {}，路径: {}", e.getMessage(), request.getRequestURI());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "凭证校验失败，安全审计拒绝");
        }
    }

    // 💡 注意：此处原本的 afterCompletion 被彻底移除！
    // 清理大权全面移交给外层无放行的 GlobalLogInterceptor，防止由于放行逻辑漏掉导致内存泄漏。
}