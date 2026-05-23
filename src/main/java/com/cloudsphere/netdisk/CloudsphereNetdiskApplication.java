package com.cloudsphere.netdisk;

import org.mybatis.spring.annotation.MapperScan; // 1. 核心引入
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.cloudsphere.netdisk.mapper") // 2. 核心追加：指定你的 Mapper 接口所在包路径
public class CloudsphereNetdiskApplication {

    static void main(String[] args) {
        SpringApplication.run(CloudsphereNetdiskApplication.class, args);
    }

}