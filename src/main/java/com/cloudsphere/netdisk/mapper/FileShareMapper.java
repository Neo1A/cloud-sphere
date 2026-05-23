package com.cloudsphere.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudsphere.netdisk.entity.FileShare;
// 🎯 核心更正：删除原先错误的 org.apache.annotation.Mapper; 替换为以下正规路径：
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileShareMapper extends BaseMapper<FileShare> {
}