package com.cloudsphere.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudsphere.netdisk.entity.UploadSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 🚀 核心重构：分布式文件并发上传会话状态机持久层
 */
@Mapper
public interface UploadSessionMapper extends BaseMapper<UploadSession> {
}