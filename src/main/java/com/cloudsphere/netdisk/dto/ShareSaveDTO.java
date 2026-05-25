package com.cloudsphere.netdisk.dto;

import lombok.Data;

@Data
public class ShareSaveDTO {
    private String shortLink;       // 短链特征码
    private String extractionCode;   // 4位提取码（无密码分享可传空）
    private Long targetParentId;     // 目标转存目录ID（默认传 0 代表根目录）
}