package com.cloudsphere.netdisk.common.exception;

import com.cloudsphere.netdisk.common.api.ResultCode;

/**
 * 专门针对工号、用户名格式/长度不合规的小异常
 */
public class UsernameFormatException extends UserDomainException {

    public UsernameFormatException(String specificMessage) {
        // 🚀 核心修正：直接透传 ResultCode 枚举对象本身，干掉 .getCode()
        // 严格对齐基类 BusinessException(ResultCode, String) 的强类型签名
        super(ResultCode.USERNAME_FORMAT_INVALID, specificMessage);
    }
}