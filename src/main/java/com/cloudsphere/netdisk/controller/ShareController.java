package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.api.ApiResponse; // 🎯 刚性引入你真实的响应体，根治编译失败
import com.cloudsphere.netdisk.dto.ShareCreateDTO;
import com.cloudsphere.netdisk.dto.ShareSaveDTO;
import com.cloudsphere.netdisk.service.ShareService;
import com.cloudsphere.netdisk.vo.ShareVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
// 🎯 核心整流：刚性对齐 WebMvcConfig 中的白名单前缀，防止被拦截器阻断引发 401
@RequestMapping(value = "/file/share", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * 1. 极光时效性分享链接创建入口 (需登录凭证)
     * POST /file/share/create
     */
    @PostMapping("/create")
    public ApiResponse<ShareVO> createShare(@Validated @RequestBody ShareCreateDTO dto) {
        ShareVO shareVO = shareService.createShare(dto);
        return ApiResponse.success(shareVO);
    }

    /**
     * 2. 🔓 匿名白名单：获取分享链接基本文件元数据 (免登录)
     * GET /file/share/info/{shortLink}
     */
    @GetMapping("/info/{shortLink}")
    public ApiResponse<Map<String, Object>> getShareInfo(@PathVariable String shortLink) {
        return ApiResponse.success(shareService.getShareInfo(shortLink));
    }

    /**
     * 3. 🔓 匿名白名单：校验 4 位随机提取口令并返回资产数据 (免登录，完美咬合前端 JSON 协议)
     * POST /file/share/verify
     */
    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> verifyShareCode(@RequestBody Map<String, String> body) {
        String shortLink = body.get("shortLink");
        String extractionCode = body.get("extractionCode");

        // 执行服务层密码刚性核验
        shareService.verifyShareCode(shortLink, extractionCode);

        // 密码通过后，顺手捞出虚拟树文件元数据，打包回传给前端 ShareModal.vue 渲染
        Map<String, Object> fileInfo = shareService.getShareInfo(shortLink);
        return ApiResponse.success(fileInfo);
    }

    /**
     * GET /file/share/download/{shortLink}
     */
    @GetMapping("/download/{shortLink}")
    public void anonymousDownload(
            @PathVariable String shortLink,
            @RequestParam(value = "extractionCode", required = false) String extractionCode,
            HttpServletResponse response) {
        shareService.anonymousDownload(shortLink, extractionCode, response);
    }

    @PostMapping("/save")
    public ApiResponse<Void> saveToMyDrive(@RequestBody ShareSaveDTO dto) {
        shareService.saveToMyDrive(dto);
        return ApiResponse.success();
    }
}