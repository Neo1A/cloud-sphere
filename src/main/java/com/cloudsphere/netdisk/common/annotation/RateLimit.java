package com.cloudsphere.netdisk.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 限流核心 Key 的前缀
     */
    String key() default "cloudsphere:ratelimit:";

    /**
     * 时间窗口长度（单位：秒），默认 60 秒
     */
    int time() default 60;

    /**
     * 在该时间窗口内允许的最大访问次数，默认 10 次
     */
    int count() default 10;
}