package com.cloudsphere.netdisk.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 极光网盘：返回给前端展现的多租户文件/目录列表对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileInfoVO {

    private Long id;
    private String name;
    private boolean isFolder;
    private Long size;
    private String sha256;
    private String updateTime;
}