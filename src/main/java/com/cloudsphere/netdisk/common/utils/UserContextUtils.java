package com.cloudsphere.netdisk.common.utils;

import lombok.Data;

/**
 * 极光网盘：线程上下文安全隔离工具（升级版载荷模型）
 */
public class UserContextUtils {

    // 🚀 核心质变：将 ThreadLocal 容器从离散的 Long 升级为完备的领域 Session 载荷
    private static final ThreadLocal<UserSession> USER_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 【向下兼容防线】保留老方法签名，防止项目其它旧代码编译报错
     * 如果老代码只传了 userId，内部自动为其动态升级包装
     */
    public static void setUserId(Long userId) {
        UserSession session = USER_THREAD_LOCAL.get();
        if (session == null) {
            session = new UserSession();
        }
        session.setUserId(userId);
        USER_THREAD_LOCAL.set(session);
    }

    /**
     * 🚀 新增标准入口：存入当前线程的全量完备 Session 载荷
     */
    public static void set(UserSession session) {
        USER_THREAD_LOCAL.set(session);
    }

    /**
     * 🚀 新增标准入口：获取当前线程的全量完备 Session 载荷
     */
    public static UserSession get() {
        return USER_THREAD_LOCAL.get();
    }

    /**
     * 兼容性及便利性封装：一键捞取当前操作员工 ID
     */
    public static Long getUserId() {
        UserSession session = USER_THREAD_LOCAL.get();
        return session != null ? session.getUserId() : null;
    }

    /**
     * 便利性封装：一键捞取当前操作员工账户名
     */
    public static String getUsername() {
        UserSession session = USER_THREAD_LOCAL.get();
        return session != null ? session.getUsername() : null;
    }

    /**
     * 便利性封装：一键捞取当前操作员工所属部门 ID
     */
    public static Long getDeptId() {
        UserSession session = USER_THREAD_LOCAL.get();
        return session != null ? session.getDeptId() : null;
    }

    /**
     * 🎯 核心应用：全链路任何角落，直接调用此方法，秒级获取当前请求的物理客户端真实 IP
     */
    public static String getCurrentIp() {
        UserSession session = USER_THREAD_LOCAL.get();
        return session != null ? session.getCurrentIp() : "unknown";
    }

    /**
     * 物理安全闭闸：防高并发高负载下 ThreadLocal 内存泄漏
     */
    public static void clear() {
        USER_THREAD_LOCAL.remove();
    }

    /**
     * 🚀 关键修正：必须加上 static 关键字声明为静态内部类
     * 只有这样才能在拦截器中脱离外部类实例直接 new 出来
     */
    @Data
    public static class UserSession {
        private Long userId;
        private String username;
        private Long deptId;
        private String currentIp; // 绑定当前请求的客户端真实物理IP
    }
}