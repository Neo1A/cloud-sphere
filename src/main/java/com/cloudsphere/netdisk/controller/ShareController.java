package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.api.CommonResult; // 假设你的项目全局包装类名为 CommonResult
import com.cloudsphere.netdisk.dto.ShareCreateDTO;
import com.cloudsphere.netdisk.service.ShareService;
import com.cloudsphere.netdisk.vo.ShareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * 🎯 极光时效性分享链接创建入口
     * POST /shares
     */
    @PostMapping
    public CommonResult<ShareVO> createShare(@Validated @RequestBody ShareCreateDTO dto) {
        ShareVO shareVO = shareService.createShare(dto);
        return CommonResult.success(shareVO); // 完美向前端 FileCabinet 喂入高保真数据流
    }
}