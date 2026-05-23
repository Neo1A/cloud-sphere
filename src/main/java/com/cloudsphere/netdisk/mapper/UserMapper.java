package com.cloudsphere.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudsphere.netdisk.entity.User;
import org.apache.ibatis.annotations.Mapper; // 核心修正：改成完整的 MyBatis 正宗路径

@Mapper // 标记为 MyBatis 的 Mapper 接口，配合主启动类的 @MapperScan 自动生效
public interface UserMapper extends BaseMapper<User> {
    // 零代码，BaseMapper 已经动态注入了所有单表的增删改查
}