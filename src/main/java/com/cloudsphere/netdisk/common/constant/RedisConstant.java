package com.cloudsphere.netdisk.common.constant;

public class RedisConstant {

    /**
     * 账户登录失败计数器前缀（防暴力破解）
     * 格式：cloudsphere:login:fail:{username} -> 失败次数
     */
    public static final String LOGIN_FAIL_PREFIX = "cloudsphere:login:fail:";

    /**
     * 大文件分片上传成功索引集合（断点续传核心）
     * 格式：cloudsphere:upload:chunks:{fileIdentifier} -> Set[chunkNumber]
     */
    public static final String UPLOAD_CHUNKS_PREFIX = "cloudsphere:upload:chunks:";

    /**
     * 登录防刷锁有效时间（如：连错5次锁定15分钟）
     */
    public static final long LOGIN_LOCK_MINUTES = 15L;

    /**
     * 分片上传状态缓存有效期（默认未完成大文件留存 7天）
     */
    public static final long UPLOAD_CHUNKS_EXPIRE_DAYS = 7L;
}