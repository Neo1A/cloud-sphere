package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.UserContext;
import com.cloudsphere.netdisk.dto.ShareCreateDTO;
import com.cloudsphere.netdisk.entity.FileShare;
import com.cloudsphere.netdisk.entity.UserFile;
import com.cloudsphere.netdisk.entity.FileInfo;
import com.cloudsphere.netdisk.mapper.FileShareMapper;
import com.cloudsphere.netdisk.mapper.UserFileMapper;
import com.cloudsphere.netdisk.mapper.FileInfoMapper;
import com.cloudsphere.netdisk.service.ShareService;
import com.cloudsphere.netdisk.vo.ShareVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final FileShareMapper fileShareMapper;
    private final UserFileMapper userFileMapper;
    private final FileInfoMapper fileInfoMapper; // 🎯 注入物理映射，用以穿透多租户壁垒

    @Override
    public ShareVO createShare(ShareCreateDTO dto) {
        Long userId = UserContext.getUserId();

        UserFile userFile = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getId, dto.getUserFileId())
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getDeleted, 0));

        if (userFile == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

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
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            try {
                String normalized = input.trim();
                if (normalized.length() == 16) { normalized += ":00"; }
                expireTime = LocalDateTime.parse(normalized, formatter);
                if (expireTime.isBefore(LocalDateTime.now())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "到期时间不能早于当前时间");
                }
            } catch (Exception e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "到期时间格式应为 yyyy-MM-dd HH:mm:ss");
            }
        }

        String extractionCode = null;
        if (Boolean.TRUE.equals(dto.getNeedCode())) {
            extractionCode = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        }

        String shortLink = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        FileShare fileShare = new FileShare();
        fileShare.setUserId(userId);
        fileShare.setUserFileId(dto.getUserFileId());
        fileShare.setShortLink(shortLink);
        fileShare.setExtractionCode(extractionCode);
        fileShare.setExpireTime(expireTime);
        fileShare.setCreateTime(LocalDateTime.now());
        fileShareMapper.insert(fileShare);

        log.info("🟢【分享网关】用户 [{}] 成功为资产 [{}] 创建链接: {}", userId, userFile.getFileName(), shortLink);

        ShareVO vo = new ShareVO();
        vo.setShareUrl("/s/" + shortLink);
        vo.setExtractionCode(extractionCode);
        vo.setExpireTime(expireTime != null ? expireTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "永久有效");
        return vo;
    }

    /**
     * 🎯 业务落盘 2：获取匿名分享元数据逻辑
     */
    @Override
    public Map<String, Object> getShareInfo(String shortLink) {
        FileShare fileShare = fileShareMapper.selectOne(new LambdaQueryWrapper<FileShare>().eq(FileShare::getShortLink, shortLink));
        if (fileShare == null) { throw new BusinessException(ResultCode.PARAM_ERROR, "分享资产链路不存在"); }
        if (fileShare.getExpireTime() != null && fileShare.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该极光分享链接已过期失效");
        }

        UserFile userFile = userFileMapper.selectById(fileShare.getUserFileId());
        if (userFile == null || userFile.getDeleted() == 1) { throw new BusinessException(ResultCode.FILE_NOT_FOUND); }

        Map<String, Object> info = new HashMap<>();
        info.put("fileName", userFile.getFileName());
        info.put("isDir", userFile.getIsDir());
        info.put("needCode", fileShare.getExtractionCode() != null);
        info.put("createTime", fileShare.getCreateTime());
        return info;
    }

    /**
     * 🎯 业务落盘 3：校验提取码逻辑
     */
    @Override
    public void verifyShareCode(String shortLink, String extractionCode) {
        FileShare fileShare = fileShareMapper.selectOne(new LambdaQueryWrapper<FileShare>().eq(FileShare::getShortLink, shortLink));
        if (fileShare == null) { throw new BusinessException(ResultCode.PARAM_ERROR, "分享资产链路不存在"); }
        if (fileShare.getExpireTime() != null && fileShare.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该极光分享链接已过期失效");
        }
        if (fileShare.getExtractionCode() != null && !fileShare.getExtractionCode().equals(extractionCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "提取口令错误，拒绝解除密码锁");
        }
    }

    /**
     * 🎯 业务落盘 4：匿名直连高性能流式刷盘下载 (打破鉴权壁垒，直击物理层)
     */
    @Override
    public void anonymousDownload(String shortLink, String extractionCode, HttpServletResponse response) {
        // 1. 安全风控前置校验
        FileShare fileShare = fileShareMapper.selectOne(new LambdaQueryWrapper<FileShare>().eq(FileShare::getShortLink, shortLink));
        if (fileShare == null) { throw new BusinessException(ResultCode.PARAM_ERROR, "分享链接不存在"); }
        if (fileShare.getExpireTime() != null && fileShare.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "链接已失效");
        }
        if (fileShare.getExtractionCode() != null && !fileShare.getExtractionCode().equals(extractionCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "防盗刷机制拦截：提取口令不匹配");
        }

        UserFile userFile = userFileMapper.selectById(fileShare.getUserFileId());
        if (userFile == null || userFile.getDeleted() == 1) { throw new BusinessException(ResultCode.FILE_NOT_FOUND); }
        if (userFile.getIsDir()) { throw new BusinessException(ResultCode.PARAM_ERROR, "暂不支持整个文件夹直接匿名下载，请进入后单选文件"); }

        // 2. 物理资产寻址
        FileInfo fileInfo = fileInfoMapper.selectById(userFile.getFileInfoId());
        if (fileInfo == null) { throw new BusinessException(ResultCode.FILE_NOT_FOUND, "物理文件索引丢失"); }

        File physicalFile = new File(fileInfo.getFilePath());
        if (!physicalFile.exists()) { throw new BusinessException(ResultCode.FILE_NOT_FOUND, "玩客云物理磁盘资产丢失"); }

        // 3. 内核级二进制流高吞吐传输大合并
        try {
            response.setContentType("application/octet-stream");
            // 解决跨平台、多浏览器环境下载时中文文件名乱码死穴
            String encodedName = URLEncoder.encode(userFile.getFileName(), "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"");
            response.setContentLengthLong(physicalFile.length());

            try (FileInputStream fis = new FileInputStream(physicalFile);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[8192]; // 8KB 高频缓冲区
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            }
            log.info("【极光匿名流控】文件 [{}] 成功匿名推流分发完毕", userFile.getFileName());
        } catch (IOException e) {
            log.warn("【极光匿名流控】匿名用户中途断开了文件网络传输流 (Broken Pipe)");
        }
    }
}