package com.cloudsphere.netdisk.common.api;

import lombok.Data;
import java.io.Serializable;

@Data
public class ApiResponse<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> success() {
        return restResult(null, ResultCode.SUCCESS);
    }

    public static <T> ApiResponse<T> success(T data) {
        return restResult(data, ResultCode.SUCCESS);
    }

    public static <T> ApiResponse<T> failed(ResultCode resultCode) {
        return restResult(null, resultCode);
    }

    public static <T> ApiResponse<T> failed(int code, String message) {
        ApiResponse<T> apiResponse = new ApiResponse<>();
        apiResponse.setCode(code);
        apiResponse.setMessage(message);
        return apiResponse;
    }

    private static <T> ApiResponse<T> restResult(T data, ResultCode resultCode) {
        ApiResponse<T> apiResponse = new ApiResponse<>();
        apiResponse.setCode(resultCode.getCode());
        apiResponse.setMessage(resultCode.getMessage());
        apiResponse.setData(data);
        return apiResponse;
    }
}