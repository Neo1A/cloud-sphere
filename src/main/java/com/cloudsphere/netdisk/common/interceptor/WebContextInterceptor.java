package com.cloudsphere.netdisk.common.interceptor;

import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.UserContextUtils;
import com.cloudsphere.netdisk.common.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.cloudsphere.netdisk.common.utils.JwtUtils;

@Component
public class WebContextInterceptor implements HandlerInterceptor {
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 获取请求头携带的 Token 密匙
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "凭证已失效，请重新登录");
        }

        // 2. 🎯 直接利用我们刚刚增强的 JwtUtils 快捷语义方法直接抽取（类型严丝合缝）
        Long userId = jwtUtils.getUserIdFromToken(token);
        String username = jwtUtils.getUsernameFromToken(token);
        Long deptId = jwtUtils.getDeptIdFromToken(token);

        if (userId == null || username == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "凭证解析故障，安全审计失败");
        }

        // 3. 🎯 创建上一轮重构出来的 UserSession 完备实体对象
        UserContextUtils.UserSession session = new UserContextUtils.UserSession();
        session.setUserId(userId);
        session.setUsername(username);
        session.setDeptId(deptId);

        // 4. 🚀 强力接入你的 IpUtils：穿透 Nginx 反向代理层，压入客户端真实物理 IP
        session.setCurrentIp(IpUtils.getIpAddr(request));

        // 5. 稳稳打入 ThreadLocal 线程上下文，全链路就地激活风控与数据审计
        UserContextUtils.set(session);

        return true;
    }
}