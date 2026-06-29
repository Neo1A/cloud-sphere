package com.cloudsphere.netdisk.service;

import com.cloudsphere.netdisk.dto.FolderCreateDTO;
import com.cloudsphere.netdisk.vo.FileInfoVO;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface FileService {
    void createFolder(FolderCreateDTO dto);
    List<FileInfoVO> listFiles(Long parentId);

    void uploadPhysicalFile(MultipartFile file, String sha256, Long parentId, String fileName, Long deptId, Long repoId);
    void deleteFile(Long fileId);
    void downloadFileStream(Long fileId, jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response);
    void downloadFolderStream(Long folderId, HttpServletResponse response);
}