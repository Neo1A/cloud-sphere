package com.cloudsphere.netdisk;

import org.mybatis.spring.annotation.MapperScan; // 🎯 必须引入这个注解
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// 🎯 核心注入：刚性指定 MyBatis 接口的扫描老巢，确保该路径下所有的 Mapper 都能安全变成 Bean
@MapperScan("com.cloudsphere.netdisk.mapper")
public class CloudsphereNetdiskApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudsphereNetdiskApplication.class, args);
    }
}