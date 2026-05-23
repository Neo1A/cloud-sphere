package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.JwtUtils;
import com.cloudsphere.netdisk.entity.User;
import com.cloudsphere.netdisk.mapper.UserMapper;
import com.cloudsphere.netdisk.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    // 常驻加盐混淆密匙（绝对不可变更，否则历史用户密码将全部失效）
    private static final String CRYPTO_SALT = "cloudsphere_secure_salt_2026";

    /**
     * 1. 真实 MySQL 注册逻辑
     */
    @Override
    public void register(String username, String password) {
        // 使用 Lambda 表达式构建流式查询，优雅、防止字段拼写错误
        User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (existUser != null) {
            log.warn("注册失败：用户名 [{}] 已被抢占", username);
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 密码安全哈希脱敏
        String encryptPassword = DigestUtils.sha256Hex(password + CRYPTO_SALT);

        // 构造实体
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptPassword);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 执行真实数据库插入 (SQL: INSERT INTO user ...)
        userMapper.insert(user);
        log.info("新用户持久化成功！用户名: {}, 自动分配雪花ID: {}", username, user.getId());
    }

    /**
     * 2. 真实 MySQL 登录与 JWT 签发逻辑
     */
    @Override
    public String login(String username, String password) {
        // 根据用户名在真实数据库检索 (SQL: SELECT * FROM user WHERE username = ?)
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            log.warn("登录失败：用户名 [{}] 不存在", username);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 比对数据库里的哈希密文
        String encryptPassword = DigestUtils.sha256Hex(password + CRYPTO_SALT);
        if (!user.getPassword().equals(encryptPassword)) {
            log.warn("登录失败：用户 [{}] 密码比对错误", username);
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        log.info("用户 [{}] 一阶段密码验证通过，成功登录系统", username);
        // 调用之前的工具，下发无状态通行证
        return jwtUtils.generateToken(user.getId(), user.getUsername());
    }
}