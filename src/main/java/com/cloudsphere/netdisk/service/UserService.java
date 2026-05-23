package com.cloudsphere.netdisk.service;

public interface UserService {
    /**
     * 用户注册
     */
    void register(String username, String password);

    /**
     * 用户登录
     * @return 签发的 JWT Token
     */
    String login(String username, String password);
}