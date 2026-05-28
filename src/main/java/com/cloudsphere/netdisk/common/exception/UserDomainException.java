package com.cloudsphere.netdisk.common.exception;

import com.cloudsphere.netdisk.common.api.ResultCode;

/**
 * 用户域专属核心异常基类
 */
public class UserDomainException extends BusinessException {
    public UserDomainException(ResultCode resultCode) {
        super(resultCode);
    }
    public UserDomainException(ResultCode code, String message) {
        super(code, message);
    }
}