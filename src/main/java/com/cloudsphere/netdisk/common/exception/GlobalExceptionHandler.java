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
import org.springframework.web.multipart.MultipartException;

import java.io.EOFException;
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
     * 🎯 核心新增：专门拦截多部分表单解析异常（文件上传中途被掐断）
     * 完美的判定机制，防止用户取消上传时 ERROR 日志爆屏
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException e) {
        Throwable rootCause = e.getRootCause();

        // 判定条件：根本原因属于 EOFException 或者是 Tomcat 封装的 ClientAbortException
        if (rootCause instanceof EOFException ||
                (rootCause != null && "org.apache.catalina.connector.ClientAbortException".equals(rootCause.getClass().getName())) ||
                (e.getMessage() != null && e.getMessage().contains("EOFException"))) {

            // 降级为 INFO 级别打印，证明这是一次受控的物理流断开，非系统崩溃
            log.info("【极光物理传输流控】网络管道闭环：用户主动取消上传或关闭了浏览器，写流通道已安全释放。");

            // 此时客户端连接已经关闭，写回任何数据都无意义，直接返回 200 或 HTTP 204 无内容即可
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        // 排除掉用户取消情况后，如果是真的上传格式错误，再进行常规警告
        log.warn("前端多部分表单（Multipart）物理写流解析失败: {}", e.getMessage());
        return buildUtf8Response(ApiResponse.failed(ResultCode.PARAM_ERROR.getCode(), "文件资产解析失败，请检查流完整性"), HttpStatus.BAD_REQUEST);
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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
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
        // 🎯 补充判定：防止某些特殊的底层客户端退出未被 MultipartException 包裹，直接冲进兜底异常中
        String exceptionName = e.getClass().getName();
        if ("org.apache.catalina.connector.ClientAbortException".equals(exceptionName) ||
                (e.getMessage() != null && e.getMessage().contains("Broken pipe"))) {
            log.info("【极光物理传输流控】读写管道由于客户端主动退出而中途断开（Broken pipe）。");
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        log.error("系统发生未捕获的严重未知故障:", e);
        return buildUtf8Response(ApiResponse.failed(ResultCode.SYSTEM_ERROR), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}