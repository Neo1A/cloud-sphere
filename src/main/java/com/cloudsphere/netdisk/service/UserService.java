package com.cloudsphere.netdisk.service;

public interface UserService {
    /**
     * 企业员工入职注册（升级版）
     * * @param username   唯一工号/域账户名
     *
     * @param password 明文密码（后端执行 SHA-256 加盐脱敏）
     * @param realName 员工真实姓名（供审批流与协作区域人性化渲染）
     * @param deptId   所属科室部门的物理自增 ID（关联 department 表）
     * @param role     行政岗位角色白名单（ADMIN, MINER_DIRECTOR, VICE_DIRECTOR, SECTION_CHIEF, USER）
     */
    void register(String username, String password, String realName, Long deptId, String role);

    /**
     * 用户登录
     *
     * @return 签发的分布式无状态 JWT Token
     */
    String login(String username, String password);

    // UserService 接口添加方法

    /**
     * 用户禁用
     */
}