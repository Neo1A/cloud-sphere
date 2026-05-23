package com.cloudsphere.netdisk.common.exception;

import com.cloudsphere.netdisk.common.api.ResultCode;
import lombok.Getter;

/**
 * 🎯 极光全局业务统一异常大闸：支持动态覆盖错误文本
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;
    private final String customMessage; // 🎯 引入自定义动态描述载体

    /**
     * 1. 传统单参数常规构造器
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.customMessage = resultCode.getMessage();
    }

    /**
     * 2. 🟢 核心补齐：双参数重载构造器！直接物理洗净 ShareServiceImpl 编译红字
     */
    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.resultCode = resultCode;
        this.customMessage = customMessage;
    }
}