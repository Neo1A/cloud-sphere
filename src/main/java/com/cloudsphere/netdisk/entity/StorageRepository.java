package com.cloudsphere.netdisk.entity;

import lombok.Data;

@Data
public class StorageRepository {

    private long id;
    private String repoName;
    private long repoType;
    private long deptId;

}
