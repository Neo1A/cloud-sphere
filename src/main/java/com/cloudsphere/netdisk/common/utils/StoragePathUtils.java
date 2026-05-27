package com.cloudsphere.netdisk.common.utils;

import java.io.File;

/**
 * 🚀 核心重构：基于物理/逻辑文件 ID 的二级哈希分片路径生成器
 */
public class StoragePathUtils {

    /**
     * 根据文件 ID 定向计算二级哈希相对路径
     *
     * @param fileId 文件主键 ID (如 fileInfoId 或逻辑 fileId)
     * @return 相对分片目录拓扑，例如: "a3/5b/"
     */
    public static String getHashShardedFolder(Long fileId) {
        if (fileId == null) {
            return "00" + File.separator + "00" + File.separator;
        }

        // 1. 引入扰动函数：混合原始哈希值的高位与低位，加大低位随机性，确保极佳的离散度
        int hash = fileId.hashCode();
        hash ^= (hash >>> 16);

        // 2. 提取一级分片：取低 8 位 (对应十六进制 00 ~ ff，共 256 个桶)
        int level1Bucket = hash & 0xFF;

        // 3. 提取二级分片：右移 8 位后取低 8 位 (对应十六进制 00 ~ ff，共 256 个子桶)
        int level2Bucket = (hash >> 8) & 0xFF;

        // 4. 格式化为两位精度的十六进制小写目录
        String level1Dir = String.format("%02x", level1Bucket);
        String level2Dir = String.format("%02x", (hash >> 8) & 0xFF);

        // 5. 拼装带有物理操作系统隔离符的相对目录骨架
        return level1Dir + File.separator + level2Dir + File.separator;
    }

    /**
     * 获取含文件名的全量相对存储路径
     *
     * @param fileId    文件主键 ID
     * @param extension 文件真实物理后缀名 (可选，如 ".dat", ".mp4")
     * @return 完整相对路径，例如: "a3/5b/1029384.dat"
     */
    public static String getFullShardedPath(Long fileId, String extension) {
        String suffix = (extension == null || extension.isEmpty()) ? ".dat" :
                (extension.startsWith(".") ? extension : "." + extension);
        return getHashShardedFolder(fileId) + fileId + suffix;
    }
}