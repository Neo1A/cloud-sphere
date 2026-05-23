package com.cloudsphere.netdisk.controller;

import com.cloudsphere.netdisk.common.api.ApiResponse;
import com.cloudsphere.netdisk.dto.FolderCreateDTO;
import com.cloudsphere.netdisk.service.FileService;
import com.cloudsphere.netdisk.vo.FileInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 1. 常规单文件物理直传 / 直传秒传防爆大闸接口
     */
    @PostMapping("/upload")
    public ApiResponse<Void> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sha256") String sha256,
            @RequestParam("parentId") Long parentId,
            @RequestParam("fileName") String fileName) {
        log.info("【直传服务】收到物理文件上传请求，文件名: {}, 哈希: {}", fileName, sha256);
        fileService.uploadPhysicalFile(file, sha256, parentId, fileName);
        return ApiResponse.success();
    }

    /**
     * 2. 获取当前目录下虚拟文件/文件夹列表
     */
    @GetMapping("/list")
    public ApiResponse<List<FileInfoVO>> listFiles(@RequestParam("parentId") Long parentId) {
        log.info("【目录服务】正在调取父级虚拟节点 [{}] 下的文件列表...", parentId);
        List<FileInfoVO> list = fileService.listFiles(parentId);
        return ApiResponse.success(list);
    }

    /**
     * 3. 🚀 高性能防越权、支持 HTTP Range 206 断点流式下载
     */
    @GetMapping("/download/{fileId}")
    public void downloadFile(
            @PathVariable Long fileId,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info("【下载流控】收到数据外发请求，准备调取逻辑虚拟树主键: {}", fileId);
        fileService.downloadFileStream(fileId, request, response);
    }

    /**
     * 4. 🆕 补全：新建虚拟文件夹接口
     * 对应 Vue 3 端: axios.post('/file/createFolder', { name, parentId })
     */
    @PostMapping("/createFolder")
    public ApiResponse<Void> createFolder(@Validated @RequestBody FolderCreateDTO dto) {
        log.info("【文件夹服务】收到新建文件夹请求，父级ID: {}, 文件夹名称: {}", dto.getParentId(), dto.getName());
        fileService.createFolder(dto);
        return ApiResponse.success();
    }

    /**
     * 5. 🆕 补全：虚拟文件删除/解绑接口
     * 对应 Vue 3 端: axios.post('/file/deleteFile/{fileId}')
     */
    @PostMapping("/deleteFile/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable Long fileId) {
        log.info("【删除服务】收到删除请求，逻辑虚拟文件 ID: {}", fileId);
        fileService.deleteFile(fileId);
        return ApiResponse.success();
    }

    @GetMapping("/download/folder/{folderId}")
    public void downloadFolder(@PathVariable Long folderId, HttpServletResponse response) {
        fileService.downloadFolderStream(folderId, response);
    }
}