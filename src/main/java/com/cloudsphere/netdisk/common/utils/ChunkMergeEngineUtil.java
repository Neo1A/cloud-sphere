package com.cloudsphere.netdisk.common.utils;

import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 🚀 规范化重构：专职负责大文件无锁 NIO 顺序追加合并的底层 I/O 引擎工具类
 */
@Slf4j
@Component
public class ChunkMergeEngineUtil {

    public void merge(List<File> sortedChunks, Path tempMergeFile) {
        try {
            if (!Files.exists(tempMergeFile.getParent())) {
                Files.createDirectories(tempMergeFile.getParent());
            }

            try (RandomAccessFile targetRAF = new RandomAccessFile(tempMergeFile.toFile(), "rw");
                 FileChannel targetChannel = targetRAF.getChannel()) {

                targetChannel.position(0);
                for (File chunk : sortedChunks) {
                    try (RandomAccessFile srcRAF = new RandomAccessFile(chunk, "r");
                         FileChannel srcChannel = srcRAF.getChannel()) {
                        long position = 0;
                        long size = srcChannel.size();
                        while (position < size) {
                            long transferred = srcChannel.transferTo(position, size - position, targetChannel);
                            if (transferred <= 0) {
                                throw new IOException("NIO 管道发生零拷贝停滞，触及磁盘 I/O 吞吐极限");
                            }
                            position += transferred;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("❌【NIO物理合并引擎故障】大文件顺序追加合并落盘崩溃", e);
            try {
                Files.deleteIfExists(tempMergeFile);
            } catch (IOException ignored) {
            }
            throw new BusinessException(ResultCode.FILE_MERGE_ERROR, "服务器执行物理资产封缄合并失败");
        }
    }
}