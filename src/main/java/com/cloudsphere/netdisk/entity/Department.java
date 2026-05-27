package com.cloudsphere.netdisk.entity;

import lombok.Data;

@Data
public class Department {

    private long id;
    private String deptName;
    private long parentId;


}
