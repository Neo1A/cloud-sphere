package com.cloudsphere.netdisk.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cloudsphere.netdisk.entity.FileInfo;
import com.cloudsphere.netdisk.mapper.FileInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 🚀 规范化重构：专职负责全服物理主表安全排他秒传核验与原生 SQL 行锁计数自增的秒传工具类
 */
@Component
@RequiredArgsConstructor
public class InstantUploadCheckUtil {

    private final FileInfoMapper fileInfoMapper;

    public FileInfo checkAndIncrement(String identifier) {
        FileInfo existPhysicalFile = fileInfoMapper.selectOne(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getFileIdentifier, identifier)
                        .ne(FileInfo::getFilePath, "LOCKING_BY_STATUS_MACHINE")
        );

        if (existPhysicalFile != null) {
            UpdateWrapper<FileInfo> uw = new UpdateWrapper<>();
            uw.eq("id", existPhysicalFile.getId()).setSql("ref_count = ref_count + 1");
            fileInfoMapper.update(null, uw);
            return existPhysicalFile;
        }
        return null;
    }
}