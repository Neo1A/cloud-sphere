package com.cloudsphere.netdisk.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 🎯 极光分享出参视图：刚性手写 Getter/Setter，彻底免疫任何 Lombok 编译期失效故障
 */
@Setter
@Getter
public class ShareVO {

    private String shareUrl;         // 匿名过桥短链
    private String extractionCode;   // 4位提取密令
    private String expireTime;       // 格式化到期时间

    // ==================== 刚性手写 Setter/Getter 传输通道 ====================

}