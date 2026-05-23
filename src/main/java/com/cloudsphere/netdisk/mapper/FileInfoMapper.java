package com.cloudsphere.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudsphere.netdisk.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}