package com.cloudsphere.netdisk.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 极光网盘：多级反向代理客户端真实 IP 提取工具
 */
public class IpUtils {

    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        // 依次穿透 Nginx, Frp, Apache 等多层反向代理，抓取最前端的真实客户端 IP
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 对于通过多个代理的情况，第一个IP为客户端真实IP, 多个IP按','分割
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // 适配本地 IPv6 回环地址
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }
}