package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.constant.UserStatusConstant;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.dto.AdminUserQueryDTO;
import com.cloudsphere.netdisk.dto.AdminUserQuotaDTO;
import com.cloudsphere.netdisk.dto.AdminUserResetPwdDTO;
import com.cloudsphere.netdisk.dto.AdminUserUpdateDTO;
import com.cloudsphere.netdisk.entity.User;
import com.cloudsphere.netdisk.mapper.UserMapper;
import com.cloudsphere.netdisk.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    // 严格引入原项目常驻高安全混淆密匙，保证重置后能成功解算
    private static final String CRYPTO_SALT = "cloudsphere_secure_salt_2026";
    private final UserMapper userMapper;

    /**
     * 功能 1.1：实现单个、多个和完整条件动态弹性筛选
     */
    @Override
    public Page<User> pageUsers(AdminUserQueryDTO queryDTO) {
        Page<User> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getPageSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 利用 condition 机制，参数不为空时激活 SQL 拼接，完美包容单条件、多条件、全条件
        wrapper.like(StringUtils.isNotBlank(queryDTO.getUsername()), User::getUsername, queryDTO.getUsername())
                .like(StringUtils.isNotBlank(queryDTO.getRealName()), User::getRealName, queryDTO.getRealName())
                .eq(queryDTO.getDeptId() != null, User::getDeptId, queryDTO.getDeptId())
                .eq(queryDTO.getStatus() != null, User::getStatus, queryDTO.getStatus())
                .eq(StringUtils.isNotBlank(queryDTO.getRole()), User::getRole, queryDTO.getRole())
                .orderByDesc(User::getCreateTime);

        Page<User> resultPage = userMapper.selectPage(pageParam, wrapper);

        // 核心风控：严防密码明文哈希以及盐值随流泄漏给前端，出库无条件熔断
        resultPage.getRecords().forEach(user -> {
            user.setPassword("******");
        });

        return resultPage;
    }

    /**
     * 功能 1.4：实现调整部门（科室），修改岗位的行政调拨功能
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserProfile(AdminUserUpdateDTO updateDTO) {
        User targetUser = userMapper.selectById(updateDTO.getUserId());
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        User updateUser = new User();
        updateUser.setId(targetUser.getId());

        // 动态赋值
        if (StringUtils.isNotBlank(updateDTO.getRealName())) {
            updateUser.setRealName(updateDTO.getRealName());
        }
        if (updateDTO.getDeptId() != null) {
            updateUser.setDeptId(updateDTO.getDeptId());
        }
        if (StringUtils.isNotBlank(updateDTO.getRole())) {
            updateUser.setRole(updateDTO.getRole().toUpperCase());
        }
        updateUser.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(updateUser);
        log.info("【管理后台】员工职能调拨成功。目标工号：{}，新部门ID：{}，新岗位：{}",
                targetUser.getUsername(), updateDTO.getDeptId(), updateDTO.getRole());
    }

    /**
     * 功能 1.2：管理员手动输入明文密码进行覆盖重置
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void resetPassword(AdminUserResetPwdDTO resetPwdDTO) {
        if (StringUtils.isBlank(resetPwdDTO.getNewPassword()) || resetPwdDTO.getNewPassword().length() < 4) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手动输入的新密码长度不合规");
        }

        User targetUser = userMapper.selectById(resetPwdDTO.getUserId());
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 强行对齐项目既有哈希算法：DigestUtils.sha256Hex(password + CRYPTO_SALT)
        String newEncryptPassword = DigestUtils.sha256Hex(resetPwdDTO.getNewPassword() + CRYPTO_SALT);

        User updateUser = new User();
        updateUser.setId(targetUser.getId());
        updateUser.setPassword(newEncryptPassword);
        updateUser.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(updateUser);
        log.info("【管理后台】管理员强制重置了员工 [{}] 的通行密码", targetUser.getUsername());
    }

    /**
     * 功能 1.3：具备【逻辑删除】与【物理删除】双轨执行办法
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteUser(Integer userId, String deleteType, Long operatorId) {
        // 1. 安全风控第一熔断：绝对禁止管理员删除自己
        if (operatorId != null && userId.equals(operatorId.intValue())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "风控警告：禁止注销或删除自身的管理员账户");
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            return; // 幂等退回
        }

        if ("physical".equalsIgnoreCase(deleteType)) {
            // 办法一：【物理删除】直接执行底层的 DELETE 物理粉碎
            userMapper.deleteById(userId);
            log.warn("【管理后台】硬物理粉碎清空用户记录！操作人：{}, 被删工号：{}", operatorId, targetUser.getUsername());
        } else {
            // 办法二：【逻辑删除】保留审计凭证，通过生命周期状态 DISABLED(0) 实施软下线隔离
            User updateUser = new User();
            updateUser.setId(targetUser.getId());
            updateUser.setStatus(UserStatusConstant.DISABLED);
            updateUser.setUpdateTime(LocalDateTime.now());

            userMapper.updateById(updateUser);
            log.info("【管理后台】执行逻辑挂起软删除。操作人：{}, 目标工号：{}", operatorId, targetUser.getUsername());
        }
    }

    /**
     * 功能 1.5：管理员手动为指定员工调整空间配额上限
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserQuota(AdminUserQuotaDTO quotaDTO) {
        if (quotaDTO.getTotalQuota() == null || quotaDTO.getTotalQuota() < 0) {
            throw new com.cloudsphere.netdisk.common.exception.BusinessException(
                    com.cloudsphere.netdisk.common.api.ResultCode.PARAM_ERROR, "分配的云盘配额容量不能为负数");
        }

        // 1. 探测员工建档是否存在
        User targetUser = userMapper.selectById(quotaDTO.getUserId());
        if (targetUser == null) {
            throw new com.cloudsphere.netdisk.common.exception.BusinessException(
                    com.cloudsphere.netdisk.common.api.ResultCode.USER_NOT_FOUND);
        }

        // 2. 刚性熔断安全防线：新调整的总配额绝对不允许小于该员工目前“已经吃掉”的物理空间
        if (quotaDTO.getTotalQuota() < targetUser.getUsedStorage()) {
            throw new com.cloudsphere.netdisk.common.exception.BusinessException(
                    com.cloudsphere.netdisk.common.api.ResultCode.PARAM_ERROR,
                    "容量缩容阻断：目标员工当前已使用空间为 " + (targetUser.getUsedStorage() / 1024 / 1024) + "MB，新总配额不得低于此阈值！");
        }

        // 3. 执行局部更新
        User updateUser = new User();
        updateUser.setId(targetUser.getId());
        updateUser.setTotalQuota(quotaDTO.getTotalQuota());
        updateUser.setUpdateTime(java.time.LocalDateTime.now());

        userMapper.updateById(updateUser);
        log.info("【管理后台-容量调控】管理员成功调整了员工 [{}] 的空间配额。旧配额: {} 字节 -> 新配额: {} 字节",
                targetUser.getUsername(), targetUser.getTotalQuota(), quotaDTO.getTotalQuota());
    }
}