package com.cloudsphere.netdisk.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class ShareCreateDTO {
    @NotNull(message = "分享的目标资产不能为空")
    private Long userFileId;

    @NotNull(message = "时效类型不能为空")
    private String expireType; // DAY_1 | DAY_7 | PERMANENT | CUSTOM (🎯 新增 CUSTOM 策略)

    private String customExpireTime; // 🎯 新增：接收前端传入的自定义 ISO 时间字符串

    private Boolean needCode;
}