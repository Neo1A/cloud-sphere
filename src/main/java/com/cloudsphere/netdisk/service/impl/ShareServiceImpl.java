package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.UserContext;
import com.cloudsphere.netdisk.dto.ShareCreateDTO;
import com.cloudsphere.netdisk.entity.FileShare;
import com.cloudsphere.netdisk.entity.UserFile;
import com.cloudsphere.netdisk.mapper.FileShareMapper;
import com.cloudsphere.netdisk.mapper.UserFileMapper;
import com.cloudsphere.netdisk.service.ShareService;
import com.cloudsphere.netdisk.vo.ShareVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final FileShareMapper fileShareMapper;
    private final UserFileMapper userFileMapper;

    @Override
    public ShareVO createShare(ShareCreateDTO dto) {
        Long userId = UserContext.getUserId();

        // 🛡️ 阻断第一关：物权判定！被分享的逻辑资产必须存在，且强制归属于当前登录用户
        UserFile userFile = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getId, dto.getUserFileId())
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getDeleted, 0));

        if (userFile == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        // 🎛️ 2. 定时失效时间核算 (全面支持自定义时长)
        // 🎯 增强型时间解析逻辑
        LocalDateTime expireTime = null;

        if ("DAY_1".equals(dto.getExpireType())) {
            expireTime = LocalDateTime.now().plusDays(1);
        } else if ("DAY_7".equals(dto.getExpireType())) {
            expireTime = LocalDateTime.now().plusDays(7);
        } else if ("CUSTOM".equals(dto.getExpireType())) {
            String input = dto.getCustomExpireTime();
            if (input == null || input.trim().isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "自定义到期时间不能为空");
            }
            // 1. 定义兼容空格格式的解析器
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            try {
                String normalized = input.trim();

                // 如果前端传过来的是 YYYY-MM-DD HH:mm (16位)
                if (normalized.length() == 16) {
                    normalized += ":00";
                }

                // 2. 使用自定义的 formatter 解析
                expireTime = LocalDateTime.parse(normalized, formatter);

                if (expireTime.isBefore(LocalDateTime.now())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "到期时间不能早于当前时间");
                }
            } catch (Exception e) {
                log.error("时间解析异常: input={}, error={}", dto.getCustomExpireTime(), e.getMessage());
                throw new BusinessException(ResultCode.PARAM_ERROR, "到期时间格式应为 yyyy-MM-dd HH:mm:ss");
            }
        }

        // 🔑 3. 提取口令随机发生器大闸
        String extractionCode = null;
        if (Boolean.TRUE.equals(dto.getNeedCode())) {
            extractionCode = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        }

        // 🔗 4. 生成 8 位无冲突全局随机短链特征码
        String shortLink = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 5. 数据持久化落盘
        FileShare fileShare = new FileShare();
        fileShare.setUserId(userId);
        fileShare.setUserFileId(dto.getUserFileId());
        fileShare.setShortLink(shortLink);
        fileShare.setExtractionCode(extractionCode);
        fileShare.setExpireTime(expireTime);
        fileShare.setCreateTime(LocalDateTime.now());
        fileShareMapper.insert(fileShare);

        log.info("🟢【分享网关】用户 [{}] 成功为资产 [{}] 创建时效分享短链: {}", userId, userFile.getFileName(), shortLink);

        // 6. 拼装符合前台高真控制台标准的 VO
        ShareVO vo = new ShareVO();
        // 此处的过桥短链会自动匹配我们在前端 App.vue 里定义的源站基本协议
        vo.setShareUrl("/s/" + shortLink);
        vo.setExtractionCode(extractionCode);
        if (expireTime != null) {
            vo.setExpireTime(expireTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            vo.setExpireTime("永久有效");
        }
        return vo;
    }
}