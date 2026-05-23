package com.cloudsphere.netdisk.common.api;

import lombok.Data;

@Data
public class CommonResult<T> {
    private int code;
    private String message;
    private T data;


    public CommonResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 🎯 成功过桥：无缝对接 ShareController.java 中的 .success 密令
     */
    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

}