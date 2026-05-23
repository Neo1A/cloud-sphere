package com.cloudsphere.netdisk.common.exception;

import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.common.api.ResultCode; // 确保引入了你的状态码
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 提取公共方法：强行注入 UTF-8 响应头，根治客户端乱码
     */
    private <T> ResponseEntity<ApiResponse<T>> buildUtf8Response(ApiResponse<T> body, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        // 强行指定 application/json;charset=UTF-8 协议大闸
        headers.setContentType(MediaType.valueOf("application/json;charset=UTF-8"));
        return new ResponseEntity<>(body, headers, status);
    }

    /**
     * 拦截已知的受控业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务逻辑触发受控拦截: code={}, message={}", e.getResultCode().getCode(), e.getMessage());
        return buildUtf8Response(ApiResponse.failed(e.getResultCode()), HttpStatus.OK);
    }

    /**
     * 拦截 DTO 参数校验失败异常 (@Validated)
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String defaultMessage = Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage();
        log.warn("前端传参格式校验未通过: {}", defaultMessage);
        return buildUtf8Response(ApiResponse.failed(ResultCode.PARAM_ERROR.getCode(), defaultMessage), HttpStatus.BAD_REQUEST);
    }

    /**
     * 拦截未知的兜底系统异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("系统发生未捕获的严重未知故障:", e);
        return buildUtf8Response(ApiResponse.failed(ResultCode.SYSTEM_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}