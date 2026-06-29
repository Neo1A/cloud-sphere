package com.cloudsphere.netdisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 极光网盘：新建文件夹业务请求对象
 */
@Data
public class FolderCreateDTO {

    @NotBlank(message = "文件夹名称不能为空")
    private String name;

    @NotNull(message = "父级ID不能为空")
    private Long parentId;

    private Long deptId;

    private Long repoId;
}