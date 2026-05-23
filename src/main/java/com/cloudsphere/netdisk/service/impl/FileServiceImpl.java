package com.cloudsphere.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudsphere.netdisk.common.api.ResultCode;
import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.UserContext;
import com.cloudsphere.netdisk.entity.FileInfo;
import com.cloudsphere.netdisk.entity.UserFile;
import com.cloudsphere.netdisk.mapper.FileInfoMapper;
import com.cloudsphere.netdisk.mapper.UserFileMapper;
import com.cloudsphere.netdisk.service.FileService;
import com.cloudsphere.netdisk.vo.FileInfoVO;
import com.cloudsphere.netdisk.dto.FolderCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final UserFileMapper userFileMapper;
    private final FileInfoMapper fileInfoMapper;

    @Value("${cloudsphere.upload.storage-path}")
    private String storageRoot;

    @PostConstruct
    public void initStorageDirectory() {
        try {
            Files.createDirectories(Paths.get(storageRoot));
            log.info("CloudSphere 极光物理存储空间就绪，路径: {}", storageRoot);
        } catch (IOException e) {
            log.error("物理存储空间初始化失败!", e);
        }
    }

    /**
     * 1. 真实 MySQL 新建虚拟文件夹
     */
    @Override
    public void createFolder(FolderCreateDTO dto) {
        Long userId = UserContext.getUserId();
        validateParentPermission(dto.getParentId(), userId);

        UserFile duplicate = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getParentId, dto.getParentId())
                .eq(UserFile::getFileName, dto.getName())
                .eq(UserFile::getIsDir, true)
                .eq(UserFile::getDeleted, 0));

        if (duplicate != null) {
            throw new BusinessException(ResultCode.DIR_ALREADY_EXISTS);
        }

        UserFile folder = new UserFile();
        folder.setUserId(userId);
        folder.setParentId(dto.getParentId());
        folder.setFileName(dto.getName());
        folder.setIsDir(true);
        folder.setDeleted(0);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        userFileMapper.insert(folder);
        log.info("用户 [{}] 成功创建虚拟文件夹: {}", userId, dto.getName());
    }

    /**
     * 2. 获取文件目录列表
     */
    @Override
    public List<FileInfoVO> listFiles(Long parentId) {
        Long userId = UserContext.getUserId();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        validateParentPermission(parentId, userId);

        List<UserFile> userFiles = userFileMapper.selectList(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getParentId, parentId)
                .eq(UserFile::getDeleted, 0));

        if (userFiles.isEmpty()) return Collections.emptyList();

        List<Long> fileInfoIds = userFiles.stream()
                .map(UserFile::getFileInfoId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Long, FileInfo> fileInfoMap = new HashMap<>();
        if (!fileInfoIds.isEmpty()) {
            List<FileInfo> fileInfos = fileInfoMapper.selectByIds(fileInfoIds);
            fileInfoMap = fileInfos.stream().collect(Collectors.toMap(FileInfo::getId, f -> f));
        }

        Map<Long, FileInfo> finalFileInfoMap = fileInfoMap;
        return userFiles.stream().map(f -> {
            FileInfoVO vo = new FileInfoVO();
            vo.setId(f.getId());
            vo.setName(f.getFileName());
            vo.setFolder(f.getIsDir());
            vo.setUpdateTime(f.getUpdateTime().format(dtf));
            if (!f.getIsDir() && f.getFileInfoId() != null) {
                FileInfo pi = finalFileInfoMap.get(f.getFileInfoId());
                if (pi != null) {
                    vo.setSize(pi.getFileSize());
                    vo.setSha256(pi.getFileIdentifier());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 4. 真实物理文件写入（安全防爆兼具直传秒传版）
     */
    @Override
    public void uploadPhysicalFile(MultipartFile file, String sha256, Long parentId, String fileName) {
        Long userId = UserContext.getUserId();
        validateParentPermission(parentId, userId);

        if (file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        FileInfo physicalFile = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getFileIdentifier, sha256));

        if (physicalFile != null) {
            log.info("【直传秒传安全大闸触发】哈希 [{}] 实体已存在，跳过物理落盘，直接复用软链接", sha256);
            physicalFile.setRefCount(physicalFile.getRefCount() + 1);
            fileInfoMapper.updateById(physicalFile);
            insertVirtualFile(userId, parentId, fileName, physicalFile.getId());
            return;
        }

        String ext = Objects.requireNonNull(file.getOriginalFilename()).contains(".") ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".")) : "";
        Path targetPath = Paths.get(storageRoot).resolve(sha256 + ext);
        try {
            file.transferTo(targetPath.toFile());

            FileInfo newPhysicalFile = new FileInfo();
            newPhysicalFile.setFileIdentifier(sha256);
            newPhysicalFile.setFilePath(targetPath.toString());
            newPhysicalFile.setFileSize(file.getSize());
            newPhysicalFile.setFileSuffix(ext);
            newPhysicalFile.setRefCount(1);
            newPhysicalFile.setCreateTime(LocalDateTime.now());
            fileInfoMapper.insert(newPhysicalFile);

            insertVirtualFile(userId, parentId, fileName, newPhysicalFile.getId());
        } catch (IOException e) {
            log.error("常规直传文件物理写入发生异常", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 5. 引用计数粉碎删除
     */
    @Override
    public void deleteFile(Long fileId) {
        Long userId = UserContext.getUserId();
        UserFile vf = userFileMapper.selectById(fileId);

        if (vf == null || !vf.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        userFileMapper.deleteById(fileId);
        if (!vf.getIsDir() && vf.getFileInfoId() != null) {
            FileInfo pf = fileInfoMapper.selectById(vf.getFileInfoId());
            if (pf != null) {
                int nextRef = pf.getRefCount() - 1;
                if (nextRef <= 0) {
                    try {
                        Files.deleteIfExists(Paths.get(pf.getFilePath()));
                        fileInfoMapper.deleteById(pf.getId());
                    } catch (IOException ignored) {}
                } else {
                    pf.setRefCount(nextRef);
                    fileInfoMapper.updateById(pf);
                }
            }
        }
    }

    /**
     * 🚀 6. 第四阶段核心落地：高性能防越权、支持 HTTP Range 断点分块下载流控引擎
     */
    @Override
    public void downloadFileStream(Long fileId, HttpServletRequest request, HttpServletResponse response) {
        // 🛡️ 阻断关卡 1：抓取安全上下文，判定当前登录的用户 ID
        Long userId = UserContext.getUserId();

        // 🛡️ 阻断关卡 2：防越权总闸！强制要求此逻辑文件不仅存在，还必须归属于当前用户
        UserFile virtualFile = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getId, fileId)
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getDeleted, 0));

        if (virtualFile == null || virtualFile.getIsDir()) {
            log.warn("🚨【防越权安全阻断】用户 [{}] 企图越权探测或下载不存在/属于他人的文件逻辑 ID: {}", userId, fileId);
            throw new BusinessException(ResultCode.FORBIDDEN); // 403 熔断
        }

        // 🛡️ 🎯 关卡 2.5：后置物理熔断墙！坚决不允许高危程序包经由非受信下载管道外发（阻断 .exe/.msi 渗透）
        String lowerFileName = virtualFile.getFileName().toLowerCase();
        List<String> blacklist = Arrays.asList("exe", "msi", "bat", "sh", "com", "cmd", "ps1");
        String extension = lowerFileName.contains(".") ? lowerFileName.substring(lowerFileName.lastIndexOf(".") + 1) : "";
        if (blacklist.contains(extension)) {
            log.warn("🚨【后端防火墙熔断】用户 [{}] 试图通过流管道绕过前端安全策略强行下载程序资产: {}", userId, lowerFileName);
            throw new BusinessException(ResultCode.FORBIDDEN); // 后端刚性 403 物理拦截
        }

        // 3. 联动提取物理仓中真正的实体路径
        FileInfo physicalFile = fileInfoMapper.selectById(virtualFile.getFileInfoId());
        if (physicalFile == null) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        Path physicalPath = Paths.get(physicalFile.getFilePath());
        if (!Files.exists(physicalPath)) {
            log.error("❌ 数据库记录存在，但物理磁盘文件丢失: {}", physicalFile.getFilePath());
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }

        long fileLength = physicalFile.getFileSize();

        // 🎯 核心更正：重整 MIME 探测逻辑，保护 Markdown 与普通源码文本流不退化为附件
        String contentType = request.getServletContext().getMimeType(virtualFile.getFileName());
        if (contentType == null) {
            if (lowerFileName.endsWith(".md")) {
                contentType = "text/markdown; charset=UTF-8";
            } else if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".log") || lowerFileName.endsWith(".json") || lowerFileName.endsWith(".ini") || lowerFileName.endsWith(".yaml") || lowerFileName.endsWith(".yml")) {
                contentType = "text/plain; charset=UTF-8";
            } else {
                contentType = "application/octet-stream";
            }
        } else if (contentType.startsWith("text/")) {
            // 如果探测到是常规 text 文本，追加 UTF-8 编码集宣告，防止前台放映厅阅览源码时乱码
            contentType = contentType + "; charset=UTF-8";
        }

        // 4. 🎛️ HTTP Range 分块区间解析器
        long startBytes = 0;
        long endBytes = fileLength - 1;
        boolean isRange = false;

        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            isRange = true;
            log.info("【HTTP Range 拦截】捕获到断点分块请求，Header: {}", rangeHeader);
            String rangeBody = rangeHeader.substring(6);
            String[] ranges = rangeBody.split("-");
            try {
                if (ranges.length >= 1 && !ranges[0].isEmpty()) {
                    startBytes = Long.parseLong(ranges[0]);
                }
                if (ranges.length >= 2 && !ranges[1].isEmpty()) {
                    endBytes = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException e) {
                startBytes = 0;
                endBytes = fileLength - 1;
            }
        }

        // 边界越界兜底，防止非法输入崩溃
        if (startBytes > endBytes || startBytes >= fileLength) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return;
        }
        if (endBytes >= fileLength) {
            endBytes = fileLength - 1;
        }
        long contentLength = endBytes - startBytes + 1;

        // 5. 🏷️ 组装符合现代多媒体预览/下载的标准响应头
        response.setContentType(contentType);
        response.setHeader("Accept-Ranges", "bytes");
        // 为防止中文文件名在前端出现乱码，进行标准的 UTF-8 编码转义
        String encodedFileName = java.net.URLEncoder.encode(virtualFile.getFileName(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "inline; filename=\"" + encodedFileName + "\"");

        if (isRange) {
            // 🎯 核心打卡：返回状态码 206 Partial Content，并声明当前传输的分块字节范围
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + startBytes + "-" + endBytes + "/" + fileLength);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
        response.setHeader("Content-Length", String.valueOf(contentLength));

        // 6. 🚀 物理磁盘高频随机定位流式派发（JVM 堆内存绝对安全）
        byte[] buffer = new byte[4096]; // 4KB 极简高频流传输滑窗
        try (RandomAccessFile raf = new RandomAccessFile(physicalPath.toFile(), "r");
             OutputStream os = response.getOutputStream()) {

            // 瞬间将物理磁盘指针移动到前端请求的起始字节位置
            raf.seek(startBytes);
            long bytesToBeRead = contentLength;

            while (bytesToBeRead > 0) {
                int readLen = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesToBeRead));
                if (readLen == -1) {
                    break;
                }
                os.write(buffer, 0, readLen);
                bytesToBeRead -= readLen;
            }
            os.flush();
            log.info("🟢【流控外发成功】文件 [{}] 区间 {}-{} 流式响应打卡完成", virtualFile.getFileName(), startBytes, endBytes);
        } catch (IOException e) {
            log.debug("【流控长连接重置】用户客户端提前中断或变更了视频/下载流连接");
        }
    }
    /**
     * 🎯 7. 终极功能落地：支持无限级虚拟子目录打包 Zip 高性能流式下载
     */
    @Override
    public void downloadFolderStream(Long folderId, HttpServletResponse response) {
        Long userId = UserContext.getUserId();

        // 🛡️ 1. 防越权总闸：判定当前选中的目录是否存在且必须归属于该用户
        UserFile rootFolder = userFileMapper.selectOne(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getId, folderId)
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getIsDir, true)
                .eq(UserFile::getDeleted, 0));

        if (rootFolder == null) {
            throw new BusinessException(ResultCode.DIR_NOT_FOUND);
        }

        // 2. 配置 Zip 打包下载的响应头参数
        response.setContentType("application/zip");
        response.setCharacterEncoding("UTF-8");
        try {
            String encodedZipName = java.net.URLEncoder.encode(rootFolder.getFileName(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "inline; filename=\"" + encodedZipName + ".zip\"");
        } catch (Exception ignored) {}

        // 3. 开启 Zip 流式压缩滑窗管道
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            // 递归灌入全量目录下的资产
            compressRecursive(folderId, "", userId, zos);
            zos.flush();
            log.info("🟢【文件夹打包成功】用户 [{}] 的虚拟目录 [{}] 已流式导出", userId, rootFolder.getFileName());
        } catch (IOException e) {
            log.debug("【打包流长连接中断】用户客户端强行中断了压缩包数据的传输管道");
        }
    }

    /**
     * 🚀 递归 DFS（深度优先搜索）下潜探测虚拟多层级物理映射压缩核心
     */
    private void compressRecursive(Long parentId, String currentPath, Long userId, java.util.zip.ZipOutputStream zos) throws IOException {
        // 抓取当前虚拟目录下挂载的全量一阶逻辑节点
        List<UserFile> children = userFileMapper.selectList(new LambdaQueryWrapper<UserFile>()
                .eq(UserFile::getUserId, userId)
                .eq(UserFile::getParentId, parentId)
                .eq(UserFile::getDeleted, 0));

        byte[] buffer = new byte[4096]; // 4KB 压缩流传输对刷滑窗

        for (UserFile child : children) {
            String entryName = currentPath + child.getFileName();

            if (child.getIsDir()) {
                // 📂 场景 A：当前子节点是一个文件夹 -> 创建 Zip 文件夹实体条目并继续向下潜
                java.util.zip.ZipEntry dirEntry = new java.util.zip.ZipEntry(entryName + "/");
                zos.putNextEntry(dirEntry);
                zos.closeEntry();

                // 递归 DFS 深入子目录
                compressRecursive(child.getId(), entryName + "/", userId, zos);
            } else {
                // 📄 场景 B：当前子节点是一个物理文件 -> 提取磁盘物理路径并流式对灌进 Zip 包
                if (child.getFileInfoId() == null) continue;
                FileInfo pf = fileInfoMapper.selectById(child.getFileInfoId());
                if (pf == null || !Files.exists(Paths.get(pf.getFilePath()))) continue;

                Path physicalPath = Paths.get(pf.getFilePath());
                java.util.zip.ZipEntry fileEntry = new java.util.zip.ZipEntry(entryName);
                zos.putNextEntry(fileEntry);

                // 物理文件极速读写
                try (java.io.InputStream is = Files.newInputStream(physicalPath)) {
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * 提取公共的虚拟逻辑树挂载动作
     */
    private void insertVirtualFile(Long userId, Long parentId, String fileName, Long fileInfoId) {
        UserFile virtualFile = new UserFile();
        virtualFile.setUserId(userId);
        virtualFile.setParentId(parentId);
        virtualFile.setFileName(fileName);
        virtualFile.setIsDir(false);
        virtualFile.setFileInfoId(fileInfoId);
        virtualFile.setDeleted(0);
        virtualFile.setCreateTime(LocalDateTime.now());
        virtualFile.setUpdateTime(LocalDateTime.now());
        userFileMapper.insert(virtualFile);
    }

    /**
     * 父级目录所有权防越权安全阻断
     */
    private void validateParentPermission(Long parentId, Long userId) {
        if (parentId.equals(0L)) return;
        UserFile pf = userFileMapper.selectById(parentId);
        if (pf == null || !pf.getUserId().equals(userId) || !pf.getIsDir()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}