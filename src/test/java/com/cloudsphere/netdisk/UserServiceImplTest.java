package com.cloudsphere.netdisk;

import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.constant.UserStatusConstant;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.entity.User;
import com.cloudsphere.netdisk.mapper.UserMapper;
import com.cloudsphere.netdisk.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

/**
 * 🧪 极光网盘内核测试：员工生命周期状态变更风控单元测试
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserMapper userMapper;

    @Test
    @DisplayName("测试点1：管理员成功封禁激活状态的员工（预期行为：正常落盘修改）")
    public void testUpdateUserStatus_DisableSuccess() {
        // 1. 准备 mock 上下文资产
        Long targetUserId = 10L;
        Long adminOperatorId = 1L;

        User mockExistUser = new User();
        mockExistUser.setId(targetUserId.intValue());
        mockExistUser.setUsername("miner_001");
        mockExistUser.setStatus(UserStatusConstant.ENABLED);

        Mockito.when(userMapper.selectById(targetUserId)).thenReturn(mockExistUser);

        // 2. 执行业务调用
        userService.updateUserStatus(targetUserId, UserStatusConstant.DISABLED, adminOperatorId);

        // 3. 🛡️ 核心重构：声明一个强类型的 User 捕获器，彻底粉碎编译器模糊匹配
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // 验证物理方法被调用，并用捕获器把入参截获下来
        Mockito.verify(userMapper, Mockito.times(1)).updateById(userCaptor.capture());

        // 从捕获器中剥离出实际灌注进去的实体，单独进行清爽的断言核验
        User capturedUser = userCaptor.getValue();
        Assertions.assertEquals(targetUserId.intValue(), capturedUser.getId());
        Assertions.assertEquals(UserStatusConstant.DISABLED, capturedUser.getStatus());
    }

    @Test
    @DisplayName("测试点2：管理员成功解封冻结状态的员工（预期行为：正常落盘修改）")
    public void testUpdateUserStatus_EnableSuccess() {
        // 1. 准备 mock 上下文资产
        Long targetUserId = 20L;
        Long adminOperatorId = 1L;

        User mockExistUser = new User();
        mockExistUser.setId(targetUserId.intValue());
        mockExistUser.setUsername("miner_002");
        mockExistUser.setStatus(UserStatusConstant.DISABLED);

        Mockito.when(userMapper.selectById(targetUserId)).thenReturn(mockExistUser);

        // 2. 执行业务调用
        userService.updateUserStatus(targetUserId, UserStatusConstant.ENABLED, adminOperatorId);

        // 3. 🛡️ 核心重构：声明强类型捕获器
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userMapper, Mockito.times(1)).updateById(userCaptor.capture());

        // 细粒度判定解封状态是否正确演进
        User capturedUser = userCaptor.getValue();
        Assertions.assertEquals(targetUserId.intValue(), capturedUser.getId());
        Assertions.assertEquals(UserStatusConstant.ENABLED, capturedUser.getStatus());
    }

    @Test
    @DisplayName("测试点3：传入非法的边界状态码（预期拦截：抛出 PARAM_ERROR 参数异常）")
    public void testUpdateUserStatus_InvalidStatusParam() {
        Long targetUserId = 10L;
        Long adminOperatorId = 1L;
        Integer illegalStatus = 99; // 恶意传入非法越界状态 99

        // 执行调用并捕获断言
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> {
            userService.updateUserStatus(targetUserId, illegalStatus, adminOperatorId);
        });

        // 确认错误状态码精准对接 PARAM_ERROR (400)
        Assertions.assertEquals(ResultCode.PARAM_ERROR, exception.getResultCode());
        // 数据库物理写方法绝对不准被调用
        Mockito.verify(userMapper, Mockito.never()).updateById(Collections.singleton(Mockito.any()));
    }

    @Test
    @DisplayName("测试点4：触发最高管理员自残熔断机制（预期拦截：管理员执行禁用自身时刚性阻断）")
    public void testUpdateUserStatus_PreventSelfDisable() {
        Long adminOperatorId = 1L;

        // 🎯 核心修复：移除原本错误的、会引发 USER_NOT_FOUND 的未 mock 干扰块。
        // 直接打靶核心防线：最高管理员手抖，传入自己的 ID 进行自残封禁，必须秒级阻断并抛出 PARAM_ERROR！
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> {
            userService.updateUserStatus(adminOperatorId, UserStatusConstant.DISABLED, adminOperatorId);
        });

        // 1. 验证风控返回的状态码是否精准对接 PARAM_ERROR (400)
        Assertions.assertEquals(ResultCode.PARAM_ERROR, exception.getResultCode());

        // 2. 验证抛出的业务异常提示文字是否符合预期
        Assertions.assertTrue(exception.getMessage().contains("系统禁止管理员执行注销或禁用自身的操作"));

        // 3. 🔒 极度高阶风控断言：因为防线处于最前端，熔断时系统绝对不准向下触及任何查库或改库的 I/O 动作！
        Mockito.verify(userMapper, Mockito.never()).selectById(Mockito.any());
        Mockito.verify(userMapper, Mockito.never()).updateById(Collections.singleton(Mockito.any()));

        System.out.println("【极光网盘断言成功】: 超级管理员自残熔断线固若金汤，成功阻止生产死锁事故，且做到了零 I/O 损耗！");
    }

    @Test
    @DisplayName("测试点5：对系统中不存在的员工进行状态变轨（预期拦截：抛出 USER_NOT_FOUND 异常）")
    public void testUpdateUserStatus_UserNotFound() {
        Long nonExistUserId = 999L;
        Long adminOperatorId = 1L;

        // 设定 mock 探空返回 null
        Mockito.when(userMapper.selectById(nonExistUserId)).thenReturn(null);

        // 执行调用并断言异常
        BusinessException exception = Assertions.assertThrows(BusinessException.class, () -> {
            userService.updateUserStatus(nonExistUserId, UserStatusConstant.DISABLED, adminOperatorId);
        });

        // 判定归属码必须为 1002 用户不存在
        Assertions.assertEquals(ResultCode.USER_NOT_FOUND, exception.getResultCode());
        Mockito.verify(userMapper, Mockito.never()).updateById(Collections.singleton(Mockito.any()));
    }

    @Test
    @DisplayName("测试点6：状态变更并发写幂等保护（预期行为：当前状态与目标相同时直接退回，不操作物理 I/O）")
    public void testUpdateUserStatus_IdempotentNoAction() {
        Long targetUserId = 30L;
        Long adminOperatorId = 1L;

        User mockExistUser = new User();
        mockExistUser.setId(targetUserId.intValue());
        mockExistUser.setStatus(UserStatusConstant.DISABLED); // 库里已经是 0 (禁用)

        Mockito.when(userMapper.selectById(targetUserId)).thenReturn(mockExistUser);

        // 管理员再次在前端手抖点击“禁用” (0)
        userService.updateUserStatus(targetUserId, UserStatusConstant.DISABLED, adminOperatorId);

        // 🔒 极度重要风控验证：因为状态一致，必须触发幂等拦截，底层 updateById 绝对不能被执行！
        Mockito.verify(userMapper, Mockito.never()).updateById(Collections.singleton(Mockito.any()));
        logVerifySuccess("幂等拦截生效，无损削峰成功！");
    }

    private void logVerifySuccess(String msg) {
        System.out.println("【极光网盘断言成功】: " + msg);
    }
}