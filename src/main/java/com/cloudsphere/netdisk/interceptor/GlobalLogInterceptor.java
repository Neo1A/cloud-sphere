package com.cloudsphere.netdisk.interceptor;

import com.cloudsphere.netdisk.common.utils.IpUtils;
import com.cloudsphere.netdisk.common.utils.UserContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class GlobalLogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1. 🚀 无死角穿透代理，抓取物理真实客户端 IP（无论是登录、注册、匿名下载还是受保护接口）
        String clientIp = IpUtils.getIpAddr(request);

        // 2. 初始化一个基础的“匿名会话载荷”
        UserContextUtils.UserSession session = new UserContextUtils.UserSession();
        session.setCurrentIp(clientIp);
        session.setUsername("ANONYMOUS_TRAFFIC"); // 默认为匿名流量标志

        // 3. 稳稳压入当前线程的 ThreadLocal 上下文
        UserContextUtils.set(session);

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod)) {
            return;
        }

        try {
            // 4. 🎯 终极归网：在请求渲染完全结束后，打印统一的流量审计日志
            // 如果后续的 JwtInterceptor 执行了，此时会话内的用户名已经被增量修正为真实员工账户名
            UserContextUtils.UserSession session = UserContextUtils.get();
            if (session != null) {
                log.info("【全量流量流转审计】操作人: [{}] | 工号ID: [{}] | 部门ID: [{}] | 真实IP: [{}] | 响应状态: [{}] | 请求路由: [{}]",
                        session.getUsername(),
                        session.getUserId() != null ? session.getUserId() : "未登录/匿名放行",
                        session.getDeptId() != null ? session.getDeptId() : "无上下文",
                        session.getCurrentIp(),
                        response.getStatus(),
                        request.getRequestURI()
                );
            }
        } finally {
            // 5. 🛑 刚性闭闸：由于此拦截器拦截 /** 且无任何放行白名单，在此执行 clear() 可以 100% 确保全服所有路由绝无内存泄漏！
            UserContextUtils.clear();
        }
    }
}