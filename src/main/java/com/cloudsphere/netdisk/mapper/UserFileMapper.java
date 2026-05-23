package com.cloudsphere.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudsphere.netdisk.entity.UserFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFileMapper extends BaseMapper<UserFile> {
}