package com.cloudsphere.netdisk.common.exception;

import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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
        headers.setContentType(MediaType.valueOf("application/json;charset=UTF-8"));
        return new ResponseEntity<>(body, headers, status);
    }

    /**
     * 🎯 核心控制：专门拦截多部分表单解析异常（文件上传中途被掐断）
     * 完美的判定机制，防止用户取消上传时 ERROR 日志爆屏
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException e) {
        Throwable rootCause = e.getRootCause();

        if (rootCause instanceof EOFException ||
                (rootCause != null && "org.apache.catalina.connector.ClientAbortException".equals(rootCause.getClass().getName())) ||
                (e.getMessage() != null && e.getMessage().contains("EOFException"))) {

            log.info("【极光物理传输流控】网络管道闭环：用户主动取消上传或关闭了浏览器，写流通道已安全释放。");
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        log.warn("前端多部分表单（Multipart）物理写流解析失败: {}", e.getMessage());
        return buildUtf8Response(ApiResponse.failed(ResultCode.PARAM_ERROR.getCode(), "文件资产解析失败，请检查流完整性"), HttpStatus.BAD_REQUEST);
    }

    /**
     * 🛡️ 纵向隔离线 A：拦截已知的受控业务异常（包含所有继承自 BusinessException 的业务小异常）
     * 只要底层抛出了属于特定领域的小异常（内部携带 1007 等靶向状态码），这里将直接透传，完全摆脱一刀切
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务领域触发特定小异常拦截: code={}, message={}", e.getResultCode().getCode(), e.getMessage());
        return buildUtf8Response(ApiResponse.failed(e.getResultCode().getCode(), e.getCustomMessage()), HttpStatus.OK);
    }

    /**
     * 拦截 DTO 参数校验失败异常 (@Validated)
     * 严格对齐底层 ApiResponse.failed(int, String) 的强类型约束
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        org.springframework.validation.FieldError fieldError = bindingResult.getFieldError();

        // ====================================================================
        // 🚀 核心修正：直接使用 int 类型维护错误码，完全契合 ApiResponse 的入参期盼
        // ====================================================================
        int finalErrorCode = ResultCode.PARAM_ERROR.getCode(); // 默认 400 通用参数错误
        String defaultMessage = "参数校验失败";

        if (fieldError != null) {
            defaultMessage = fieldError.getDefaultMessage();
            String targetField = fieldError.getField(); // 捕获具体触发违规的字段名

            // 🎯 动态路由大闸：如果是 username 违规，直接提取出 1007 这个 int 状态码
            if ("username".equals(targetField)) {
                finalErrorCode = ResultCode.USERNAME_FORMAT_INVALID.getCode(); // 🚀 提取为 1007 (int)
                log.warn("【前置校验拦截】工号/用户名格式校验未通过 -> 路由至 1007 状态码。详情: {}", defaultMessage);
            } else {
                // 非核心公共字段触发的校验，依然保持默认的 400 状态码
                log.warn("【前置校验拦截】泛型通用字段 [{}] 校验未通过: {}", targetField, defaultMessage);
            }
        }

        // ====================================================================
        // 🚀 核心修正：finalErrorCode 是 int，defaultMessage 是 String，类型严丝合缝
        // ====================================================================
        return buildUtf8Response(ApiResponse.failed(finalErrorCode, defaultMessage), HttpStatus.BAD_REQUEST);
    }
    /**
     * 拦截未知的兜底系统异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
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