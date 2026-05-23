package com.cloudsphere.netdisk.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {
    /**
     * 期待的目标角色标识，例如 "ADMIN" 或 "USER"
     */
    String value();
}