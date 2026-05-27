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
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    // 常驻加盐混淆密匙（绝对不可变更，否则历史用户密码将全部失效）
    private static final String CRYPTO_SALT = "cloudsphere_secure_salt_2026";

    // 矿业企业版合规行政岗位白名单
    private static final List<String> VALID_ROLES = Arrays.asList("ADMIN", "MINER_DIRECTOR", "VICE_DIRECTOR", "SECTION_CHIEF", "USER");

    /**
     * 1. 升级版：真实 MySQL 企业员工入职注册逻辑
     * 注意：同步需要更新 UserService 接口声明：
     * void register(String username, String password, String realName, Long deptId, String role);
     */
    @Override
    public void register(String username, String password, String realName, Long deptId, String role) {
        // 1.1 刚性验证：拦截非法岗位角色的注入，确保科层合规
        if (role == null || !VALID_ROLES.contains(role.toUpperCase())) {
            log.error("注册阻断：非法行政角色签名 [{}]", role);
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法的企业行政岗位角色");
        }

        // 1.2 唯一性探空：使用 Lambda 表达式检查工号是否重复
        User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (existUser != null) {
            log.warn("注册失败：企业工号 [{}] 已存在，禁止重复入驻", username);
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 1.3 密码安全哈希脱敏
        String encryptPassword = DigestUtils.sha256Hex(password + CRYPTO_SALT);

        // 1.4 构造契合企业版拓扑的完整实体
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptPassword);
        user.setRealName(realName);                // 注入员工真实姓名，供审批及分享树人性化渲染
        user.setDeptId(deptId);                    // 绑定所属科室部门物理ID
        user.setRole(role.toUpperCase());          // 划定行政职能级别 (ADMIN/MINER_DIRECTOR等)
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 1.5 执行真实数据库插入 (SQL: INSERT INTO user ...)
        userMapper.insert(user);
        log.info("企业新员工建档成功！工号: {}, 姓名: {}, 职能岗位: {}, 所属部门ID: {}", username, realName, role, deptId);
    }

    /**
     * 2. 升级版：真实 MySQL 企业员工登录与 JWT 签发逻辑
     */
    @Override
    public String login(String username, String password) {
        // 2.1 根据工号在真实数据库检索 (SQL: SELECT * FROM user WHERE username = ?)
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (user == null) {
            log.warn("登录失败：企业工号 [{}] 未在系统建档登记", username);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        Integer userStatus = user.getStatus();
        if (userStatus == null || userStatus != 1) {
            log.warn("登录失败：工号 [{}] 账户已禁用，状态码={}", username, userStatus);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 2.2 比对数据库里的哈希密文
        String encryptPassword = DigestUtils.sha256Hex(password + CRYPTO_SALT);
        if (!user.getPassword().equals(encryptPassword)) {
            log.warn("登录失败：工号 [{}] 密码比对错误", username);
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        log.info("员工 [{}] ({}) 验证通过，成功登录矿业数字化仓储大厅。岗位: {}, 科室ID: {}", username, user.getRealName(), user.getRole(), user.getDeptId());

        // 2.3 调用原有 JwtUtils 安全网关，下发分布式无状态通行证
        // 注意：由于底层安全过滤器 Interceptor 拦截时需要从 Token 中还原岗位与科室进行 ACL 熔断判定，
        // 建议后续按需扩展 JwtUtils.generateToken 方法，将 user.getRole() 和 user.getDeptId() 存入 Claims 载荷中。
        return jwtUtils.generateToken(user.getId(), user.getUsername());
    }

}