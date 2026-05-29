package com.cloudsphere.netdisk;

import com.cloudsphere.netdisk.common.exception.BusinessException;
import com.cloudsphere.netdisk.common.utils.*;
import com.cloudsphere.netdisk.dto.ChunkInitDTO;
import com.cloudsphere.netdisk.dto.FileMergeDTO;
import com.cloudsphere.netdisk.entity.FileInfo;
import com.cloudsphere.netdisk.entity.UploadSession;
import com.cloudsphere.netdisk.entity.UserFile;
import com.cloudsphere.netdisk.mapper.FileInfoMapper;
import com.cloudsphere.netdisk.mapper.UploadSessionMapper;
import com.cloudsphere.netdisk.mapper.UserFileMapper;
import com.cloudsphere.netdisk.service.impl.ChunkUploadServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChunkUploadServiceImplTest {

    @TempDir
    Path tempStorageRoot;

    @TempDir
    Path tempChunkRoot;

    @InjectMocks
    private ChunkUploadServiceImpl chunkUploadService;

    @Mock
    private UserFileMapper userFileMapper;

    @Mock
    private FileInfoMapper fileInfoMapper;

    @Mock
    private UploadSessionMapper sessionMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    // 🎯 补齐修复 1：显式声明事务模板 Mock 桩字段，拒绝“符号无法解析”
    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ChunkIntegrityValidateUtil fileValidator;

    @Mock
    private ChunkMergeEngineUtil mergeEngine;

    @Mock
    private UploadSessionStateMachineUtil stateMachine;

    @Mock
    private UploadTempCleanUtil tempCleaner;

    @Mock
    private InstantUploadCheckUtil instantChecker;

    // 🎯 补齐修复 2：显式声明静态 Mock 生命周期钩子字段
    private MockedStatic<UserContextUtils> userContextMock;
    private MockedStatic<StoragePathUtils> storagePathMock;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(chunkUploadService, "storageRoot", tempStorageRoot.toString());
        org.springframework.test.util.ReflectionTestUtils.setField(chunkUploadService, "chunkTempRoot", tempChunkRoot.toString());

        userContextMock = Mockito.mockStatic(UserContextUtils.class);
        userContextMock.when(UserContextUtils::getUserId).thenReturn(1001L);

        storagePathMock = Mockito.mockStatic(StoragePathUtils.class);
        storagePathMock.when(() -> StoragePathUtils.getHashShardedFolder(anyLong())).thenReturn("ab/cd/");

        // 🎯 兼容修复 3：使用强制类型转换替换 getArgument(int, Class)，彻底击穿环境版本异构
        lenient().when(StoragePathUtils.getFullShardedPath(anyLong(), anyString()))
                .thenAnswer(inv -> {
                    Long id = (Long) inv.getArgument(0);
                    String suffix = (String) inv.getArgument(1);
                    return "ab/cd/" + id + suffix;
                });

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(setOps.members(anyString())).thenReturn(Set.of("0", "1"));
        lenient().when(setOps.add(anyString(), anyString())).thenReturn(1L);
        lenient().when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        TransactionStatus mockStatus = mock(TransactionStatus.class);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<?> callback = (TransactionCallback<?>) inv.getArgument(0);
            return callback.doInTransaction(mockStatus);
        });

        lenient().doAnswer(inv -> {
            TransactionCallbackWithoutResult callback = (TransactionCallbackWithoutResult) inv.getArgument(0);
            callback.doInTransaction(mockStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        lenient().when(sessionMapper.selectOne(any())).thenReturn(null);
        lenient().when(sessionMapper.insert(any(UploadSession.class))).thenReturn(1);

        lenient().when(fileInfoMapper.insert(any(FileInfo.class))).thenAnswer(inv -> {
            FileInfo fi = (FileInfo) inv.getArgument(0);
            fi.setId(100L);
            return 1;
        });
        lenient().when(fileInfoMapper.updateById(any(FileInfo.class))).thenReturn(1);
        lenient().when(userFileMapper.insert(any(UserFile.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        if (userContextMock != null) userContextMock.close();
        if (storagePathMock != null) storagePathMock.close();
    }

    @Test
    void testInitChunkUpload_Success() {
        ChunkInitDTO dto = new ChunkInitDTO();
        dto.setIdentifier("hash1");
        dto.setRepoId(1L);
        dto.setDeptId(1L);

        Set<Integer> result = chunkUploadService.initChunkUpload(dto);
        assertThat(result).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void testInitChunkUpload_UserIdNull() {
        userContextMock.when(UserContextUtils::getUserId).thenReturn(null);
        ChunkInitDTO dto = new ChunkInitDTO();
        dto.setIdentifier("hash2");
        assertThatThrownBy(() -> chunkUploadService.initChunkUpload(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂未登录");
    }

    @Test
    void testInitChunkUpload_NullRepoOrDept() {
        ChunkInitDTO dto = new ChunkInitDTO();
        dto.setIdentifier("hash3");
        dto.setRepoId(null);
        dto.setDeptId(1L);
        assertThatThrownBy(() -> chunkUploadService.initChunkUpload(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("初始化失败：核心存储库或所属科室外键缺失");
    }

    @Test
    void testUploadChunk_Success() throws IOException {
        var mockFile = mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        doAnswer(inv -> {
            File f = (File) inv.getArgument(0);
            Files.createDirectories(f.toPath().getParent());
            Files.writeString(f.toPath(), "chunk-data");
            return null;
        }).when(mockFile).transferTo(any(File.class));

        chunkUploadService.uploadChunk(mockFile, "upload1", 0);

        Path chunkFile = tempChunkRoot.resolve("upload1").resolve("0");
        assertThat(chunkFile).exists();
        assertThat(Files.readString(chunkFile)).isEqualTo("chunk-data");
    }

    @Test
    void testUploadChunk_EmptyFile() {
        var mockFile = mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> chunkUploadService.uploadChunk(mockFile, "x", 0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void testMergeChunks_InstantUploadHit() {
        FileMergeDTO dto = buildMergeDTO("instanthash", "file.mp4", 1L, 1L, 1);
        FileInfo existFile = new FileInfo();
        existFile.setId(10L);
        when(instantChecker.checkAndIncrement("instanthash")).thenReturn(existFile);

        chunkUploadService.mergeChunks(dto);

        verify(stateMachine, never()).claimMergeLock(anyString());
        verify(mergeEngine, never()).merge(anyList(), any());
        verify(stateMachine).forceUpdateStatus("instanthash", "DONE");
        verify(tempCleaner).clearTempChunksAsync(any(Path.class), anyString());
    }

    @Test
    void testMergeChunks_LockFailed() {
        FileMergeDTO dto = buildMergeDTO("lockfail", "doc.txt", 1L, 1L, 1);
        when(instantChecker.checkAndIncrement("lockfail")).thenReturn(null);
        when(stateMachine.claimMergeLock("lockfail")).thenReturn(false);

        assertThatCode(() -> chunkUploadService.mergeChunks(dto)).doesNotThrowAnyException();
        verify(mergeEngine, never()).merge(anyList(), any());
    }

    @Test
    void testMergeChunks_Success() throws IOException {
        FileMergeDTO dto = buildMergeDTO("mergeOk", "final.txt", 1L, 1L, 2);
        List<File> mockChunks = List.of(new File("0"), new File("1"));

        when(instantChecker.checkAndIncrement("mergeOk")).thenReturn(null);
        when(stateMachine.claimMergeLock("mergeOk")).thenReturn(true);
        when(fileValidator.validateAndSort(any(Path.class), eq(2))).thenReturn(mockChunks);

        doAnswer(inv -> {
            Path tempFile = (Path) inv.getArgument(1);
            Files.createDirectories(tempFile.getParent());
            Files.createFile(tempFile);
            return null;
        }).when(mergeEngine).merge(anyList(), any(Path.class));

        chunkUploadService.mergeChunks(dto);

        verify(mergeEngine).merge(eq(mockChunks), any(Path.class));
        verify(fileInfoMapper).insert(any(FileInfo.class));
        verify(fileInfoMapper).updateById(any(FileInfo.class));
        verify(userFileMapper).insert(any(UserFile.class));
        verify(stateMachine).updateStatus("mergeOk", "MERGING", "DONE");
        verify(tempCleaner).clearTempChunksAsync(any(Path.class), anyString());
    }

    @Test
    void testMergeChunks_IOFailure() {
        FileMergeDTO dto = buildMergeDTO("ioFail", "fail.txt", 1L, 1L, 1);
        List<File> mockChunks = List.of(new File("0"));

        when(instantChecker.checkAndIncrement("ioFail")).thenReturn(null);
        when(stateMachine.claimMergeLock("ioFail")).thenReturn(true);
        when(fileValidator.validateAndSort(any(Path.class), eq(1))).thenReturn(mockChunks);

        doThrow(new RuntimeException("IO error")).when(mergeEngine).merge(anyList(), any(Path.class));

        assertThatThrownBy(() -> chunkUploadService.mergeChunks(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大文件封缄无锁合并失败，请重新尝试合并");

        verify(stateMachine).rollbackStatus("ioFail", "UPLOADING");
    }

    private FileMergeDTO buildMergeDTO(String identifier, String fileName, Long repoId, Long deptId, int totalChunks) {
        FileMergeDTO dto = new FileMergeDTO();
        dto.setIdentifier(identifier);
        dto.setFileName(fileName);
        dto.setParentId(0L);
        dto.setRepoId(repoId);
        dto.setDeptId(deptId);
        dto.setTotalChunks(totalChunks);
        return dto;
    }
}