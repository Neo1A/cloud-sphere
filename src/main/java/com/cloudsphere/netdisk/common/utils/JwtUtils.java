package com.cloudsphere.netdisk.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${cloudsphere.jwt.secret}")
    private String secret;

    @Value("${cloudsphere.jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 🚀 核心修复：生成 Token 刚性支持 Long 类型 userId
     */
    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 企业版多维拓扑拓展：同时注入部门维度 deptId 支撑协同公盘隔离
     */
    public String generateToken(Long userId, String username, Long deptId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("deptId", deptId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token 基础荷载
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从加密 Token 中快捷安全拆解出用户物理自增 ID（Long）
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null; // 契合风控闸，解析失败直接回传 null 由拦截器熔断
        }
    }

    /**
     * 从加密 Token 中快捷拆解出唯一员工工号/域账户名（String）
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("username", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从加密 Token 中快捷安全拆解出所属科室部门 ID（Long）
     */
    public Long getDeptIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            Object deptIdObj = claims.get("deptId");
            return deptIdObj != null ? Long.valueOf(deptIdObj.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}