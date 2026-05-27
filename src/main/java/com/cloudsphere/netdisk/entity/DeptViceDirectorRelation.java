package com.cloudsphere.netdisk.entity;

import lombok.Data;

@Data
public class DeptViceDirectorRelation {

    private long id;
    private long viceDirectorUserId;
    private long deptId;

}
