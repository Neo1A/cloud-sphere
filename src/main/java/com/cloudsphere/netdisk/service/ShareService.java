package com.cloudsphere.netdisk.service;

import com.cloudsphere.netdisk.dto.ShareCreateDTO;
import com.cloudsphere.netdisk.dto.ShareSaveDTO;
import com.cloudsphere.netdisk.vo.ShareVO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

public interface ShareService {
    ShareVO createShare(ShareCreateDTO dto);

    // 🎯 新增的匿名闭环服务能力
    Map<String, Object> getShareInfo(String shortLink);
    void verifyShareCode(String shortLink, String extractionCode);
    void anonymousDownload(String shortLink, String extractionCode, HttpServletResponse response);
    void saveToMyDrive(ShareSaveDTO dto);
}