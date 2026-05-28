package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.constant.UserStatusConstant;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.exception.UsernameFormatException;
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
    private static final String USERNAME_REGEX = "^[a-zA-Z0-9_]+$";
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
//    public void register(String username, String password, String realName, Long deptId, String role) {
    public void register(String username, String password, String realName) {

        // 1.1 刚性验证：拦截非法岗位角色的注入，确保科层合规
//        if (role == null || !VALID_ROLES.contains(role.toUpperCase())) {
//            log.error("注册阻断：非法行政角色签名 [{}]", role);
//            throw new BusinessException(ResultCode.PARAM_ERROR, "非法的企业行政岗位角色");
//        }

        // 1.2 唯一性探空：使用 Lambda 表达式检查工号是否重复
        User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (username == null || username.length() < 4 || username.length() > 20) {
            throw new UsernameFormatException("账号长度必须在 4-20 位之间"); // 🎯 直击痛点
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new UsernameFormatException("工号格式不正确！仅允许包含字母、数字和下划线");
        }
        if (existUser != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 1.3 密码安全哈希脱敏
        String encryptPassword = DigestUtils.sha256Hex(password + CRYPTO_SALT);

        // 1.4 构造契合企业版拓扑的完整实体
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptPassword);
        user.setRealName(realName);                // 注入员工真实姓名，供审批及分享树人性化渲染
//        user.setDeptId(deptId);                    // 绑定所属科室部门物理ID
//        user.setRole(role.toUpperCase());          // 划定行政职能级别 (ADMIN/MINER_DIRECTOR等)
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 1.5 执行真实数据库插入 (SQL: INSERT INTO user ...)
        userMapper.insert(user);
//        log.info("企业新员工建档成功！工号: {}, 姓名: {}, 职能岗位: {}, 所属部门ID: {}", username, realName, role, deptId);
        log.info("企业新员工建档成功！工号: {}, 姓名: {}", username, realName);

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

    /**
     * 3. 变更企业员工账户生命周期状态（商用级风控重构版）
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class) // 注入事务链
    public void updateUserStatus(Long id, Integer status, Long operatorId) {
        // 3.1 刚性边界值检查
        if (status == null || (status != UserStatusConstant.DISABLED && status != UserStatusConstant.ENABLED)) {
            log.error("状态变更阻断：非法的状态参数标记值 [{}]", status);
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法的状态变更标记");
        }

        // 3.2 超级风控熔断锁：最高管理员绝对禁止封禁自己，防止把自己锁死在外面的重大生产故障
        if (status == UserStatusConstant.DISABLED && id.equals(operatorId)) {
            log.warn("【风控熔断触发】超级管理员 ID:[{}] 尝试在后台禁用自身账户，系统刚性拦截！", operatorId);
            throw new BusinessException(ResultCode.PARAM_ERROR, "风控安全警告：系统禁止管理员执行注销或禁用自身的操作");
        }

        // 3.3 检索目标对象建档信息
        User targetUser = userMapper.selectById(id);
        if (targetUser == null) {
            log.warn("状态变更失败：目标员工用户 ID:[{}] 在系统中不存在", id);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 3.4 状态幂等审查：若当前库内状态已与目标状态重合，直接无损返回，避免高频磁盘 I/O 刷盘
        if (targetUser.getStatus() != null && targetUser.getStatus().equals(status)) {
            log.info("状态变更忽略：目标员工工号:[{}] 状态当前已为 {}, 自动触发幂等退回", targetUser.getUsername(), status);
            return;
        }

        // 3.5 构造干净实体执行 MyBatis-Plus 局部更新
        User updateUser = new User();
        // 如果目前实体的 id 还是 int，在此处进行安全收拢强转：updateUser.setId(id.intValue());
        updateUser.setId(id.intValue());
        updateUser.setStatus(status);
        updateUser.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(updateUser);

        log.info("【企业员工状态变更成功】操作人ADMIN-ID:[{}], 目标员工工号:[{}], 姓名:[{}], 状态演进:[{}] -> [{}]",
                operatorId, targetUser.getUsername(), targetUser.getRealName(), targetUser.getStatus(), status);
    }

}