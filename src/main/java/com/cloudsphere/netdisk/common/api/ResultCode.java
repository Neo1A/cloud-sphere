package com.cloudsphere.netdisk.common.api;

import lombok.Getter;

@Getter
public enum ResultCode {

    // 系统级通用状态码
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "暂未登录或身份凭证已过期"),
    FORBIDDEN(403, "没有相关操作权限"),
    NOT_FOUND(404, "请求资源不存在"),
    SYSTEM_ERROR(500, "服务器开小差了，请稍后再试"),

    // 用户模块相关状态码 (1000-1999)
    USER_ALREADY_EXISTS(1001, "该用户名已被注册"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),
    USER_DISABLED(1004, "该账户已被禁用"),
    LOGIN_FAIL_MANY(1005, "登录失败次数过多，请稍后再试"),

    // 文件与传输模块相关状态码 (2000-2999)
    FILE_NOT_FOUND(2001, "目标文件或文件夹不存在"),
    DIR_ALREADY_EXISTS(2002, "同级目录下已存在同名文件夹"),
    SPACE_LIMIT_EXCEEDED(2003, "网盘可用存储空间不足"),
    CHUNK_INDEX_OUT_OF_BOUND(2004, "切片序号超出合法范围"),
    FILE_MERGE_ERROR(2005, "文件合并失败，切片可能不完整"),

    // 🎯 完美归位：分配 2006 模块码，完美对接多级目录打包下载
    DIR_NOT_FOUND(2006, "逻辑文件夹不存在或已被粉碎"),

    // 限流相关状态码
    TOO_MANY_REQUESTS(429, "您的请求过于频繁，请稍后再试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}