package com.cloudsphere.netdisk.common.enums;

import lombok.Getter;

@Getter
public enum RoleEnum {
    ADMIN("ADMIN", "系统管理员"),
    MINER_DIRECTOR("MINER_DIRECTOR", "矿长"),
    VICE_DIRECTOR("VICE_DIRECTOR", "副矿长"),
    SECTION_CHIEF("SECTION_CHIEF", "科长"),
    USER("USER", "普通员工");

    private final String code;   // 存入数据库的字符串
    private final String desc;   // 中文描述

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // 根据 code 反查枚举，用于 Service 层校验
    public static RoleEnum fromCode(String code) {
        for (RoleEnum role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("无效角色代码: " + code);
    }
}