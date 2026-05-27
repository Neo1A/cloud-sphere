package com.cloudsphere.netdisk.common.utils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudsphere.netdisk.entity.UploadSession;
import com.cloudsphere.netdisk.mapper.UploadSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 🚀 规范化重构：专职负责分布式上传会话状态机推进与弹性自愈的锁控工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadSessionStateMachineUtil {

    private final UploadSessionMapper sessionMapper;

    public boolean claimMergeLock(String identifier) {
        LambdaUpdateWrapper<UploadSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UploadSession::getUploadId, identifier)
                .eq(UploadSession::getStatus, "UPLOADING")
                .set(UploadSession::getStatus, "MERGING")
                .set(UploadSession::getUpdateTime, LocalDateTime.now());
        return sessionMapper.update(null, updateWrapper) > 0;
    }

    public void updateStatus(String identifier, String fromStatus, String toStatus) {
        LambdaUpdateWrapper<UploadSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UploadSession::getUploadId, identifier)
                .eq(UploadSession::getStatus, fromStatus)
                .set(UploadSession::getStatus, toStatus)
                .set(UploadSession::getUpdateTime, LocalDateTime.now());
        sessionMapper.update(null, updateWrapper);
    }

    public void rollbackStatus(String identifier, String rollbackStatus) {
        try {
            LambdaUpdateWrapper<UploadSession> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(UploadSession::getUploadId, identifier)
                    .set(UploadSession::getStatus, rollbackStatus)
                    .set(UploadSession::getUpdateTime, LocalDateTime.now());
            sessionMapper.update(null, updateWrapper);
        } catch (Exception dbEx) {
            log.error("🚨【状态机回滚失败】大文件标识 [{}] 状态重置死锁！", identifier, dbEx);
        }
    }

    public void forceUpdateStatus(String identifier, String targetStatus) {
        LambdaUpdateWrapper<UploadSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UploadSession::getUploadId, identifier)
                .set(UploadSession::getStatus, targetStatus)
                .set(UploadSession::getUpdateTime, LocalDateTime.now());
        sessionMapper.update(null, updateWrapper);
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void repairStuckMergingSessions() {
        log.info("【上传会话状态机】拉起僵尸合并会话的自动巡检自愈功能...");
        LambdaUpdateWrapper<UploadSession> uw = new LambdaUpdateWrapper<>();
        uw.eq(UploadSession::getStatus, "MERGING")
                .lt(UploadSession::getUpdateTime, LocalDateTime.now().minusHours(1))
                .set(UploadSession::getStatus, "UPLOADING")
                .set(UploadSession::getUpdateTime, LocalDateTime.now());

        int count = sessionMapper.update(null, uw);
        if (count > 0) {
            log.warn("🚨【状态机弹性自愈】成功强行解冻了 {} 个卡死的僵尸合并会话！", count);
        }
    }
}