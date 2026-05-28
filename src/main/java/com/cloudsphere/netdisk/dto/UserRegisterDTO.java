package com.cloudsphere.netdisk.dto;

import com.cloudsphere.netdisk.common.enums.RoleEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {

    @NotBlank(message = "工号/用户名不能为空")
    @Size(min = 4, max = 20, message = "账号长度必须在 4-20 位之间")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "工号格式不正确！仅允许包含字母、数字和下划线"
    )
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在 6-32 位之间")
    private String password;

    @NotBlank(message = "员工真实姓名不能为空")
    private String realName;

//    @NotNull(message = "所属科室部门ID不能为空")
//    private Long deptId;

//    @NotNull(message = "行政职能岗位角色不能为空")
//  private String role; // 白名单：ADMIN, MINER_DIRECTOR, VICE_DIRECTOR, SECTION_CHIEF, USER
//    private RoleEnum role;
}