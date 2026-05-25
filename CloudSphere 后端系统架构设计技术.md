# **CloudSphere 极光网盘后端系统架构设计技术白皮书**

本白皮书旨在阐明 **CloudSphere（极光网盘）** 系统的底层架构设计、核心流控算法以及物理与逻辑层解耦模型。系统基于 **Spring Boot 3.x**、**MyBatis-Plus 3.5** 与 **Redis 7.0** 构建，专门针对高并发大文件读写、极简秒传、断点续传、无限虚拟子目录打包流式下载等工业级核心场景进行了深度重构与性能调优。

## **1\. 架构总览与技术选型**

### **1.1 系统架构图**

系统采用前后端分离的多路复用分层架构。前端（Vue 3 \+ Vite）通过 API 网关与后端进行高频交互，后端通过多租户隔离层、逻辑控制层及物理外发引擎对底层的 MySQL 存储和物理磁盘温区进行统一调度。

\+-------------------------------------------------------------+  
|                     Vue 3 / Vite 传输大厅                    |  
\+-------------------------------------------------------------+  
                               |  HTTP / RESTful API (JWT)  
                               v  
\+-------------------------------------------------------------+  
|                     JwtInterceptor 安全门禁                  |  
\+-------------------------------------------------------------+  
                               |   
                               v  
\+-------------------------------------------------------------+  
|                  CloudSphere 核心控制引擎                    |  
|  \+-------------------+               \+-------------------+  |  
|  |   FileController  |               |  ShareController  |  |  
|  \+-------------------+               \+-------------------+  |  
|  | ChunkUploadContr. |               |   UserController  |  |  
|  \+-------------------+               \+-------------------+  |  
\+-------------------------------------------------------------+  
                               |   
                               v  
\+-------------------------------------------------------------+  
|                       核心业务服务层                         |  
|   \+-----------------------------------------------------+   |  
|   |   FileService / ShareService / ChunkUploadService   |   |  
|   \+-----------------------------------------------------+   |  
\+-------------------------------------------------------------+  
         |                                           |  
         v (MyBatis-Plus ORM)                        v (NIO / Stream)  
\+------------------+                       \+------------------+  
| MySQL 8.0 逻辑树 |                       |  OS 物理存储温区  |  
| (user\_file 表)   |                       |  (file\_info 映射)|  
\+------------------+                       \+------------------+

### **1.2 核心技术栈骨架**

* **核心框架**：Spring Boot 3.2.x (全面拥抱原生 jakarta 依赖命名空间)。  
* **持久层 ORM**：MyBatis-Plus 3.5.x，引入 Lambda 链式构造器，杜绝魔术字符串拼接。  
* **高并发高速缓存**：Redis 7.0 (极简高可用容器化部署)，用于暂存分片记录、抗高并发分布式限流与分享防刷控制。  
* **关系型数据库**：MySQL 8.0，采用 InnoDB 存储引擎，针对树状查询构建复合索引。  
* **容器化生态**：支持 Docker Compose 一键群装，预设物理内存滑窗（如 innodb\_buffer\_pool\_size=512M）以对冲低功耗嵌入式或云端板卡。

## **2\. 物理与逻辑彻底解耦模型 (Logical-Physical Decoupling)**

传统网盘系统往往采用“物理文件与用户资产一一绑定”的粗暴模式，在发生用户间转存、重命名、移动等操作时，会引发灾难性的物理磁盘 I/O 读写。CloudSphere 彻底斩断了这一强耦合，实施了**物理与逻辑完全剥离的双轨驱动制**：

### **2.1 物理资产温区共享池 (file\_info)**

* **物理位置**：由后端配置的属性 ${cloudsphere.upload.storage-path} 指定。  
* **物理命名**：任何落盘的物理资产，其文件名无条件变更为该文件内容的全局唯一哈希指纹（SHA-256）。  
* **唯一性**：相同的物理内容在整台服务器的磁盘温区中**永远只留存一份**，从源头切断重复物理上传，数据去重率达 100%。  
* **锁管理（引用计数）**：物理资产记录引入 ref\_count（软引用计数器）。当用户 A 上传大文件时，该文件 ref\_count \= 1；当用户 B 命中该文件秒传时，物理文件保持不动，ref\_count 自动递增为 2。

### **2.2 多租户逻辑虚拟目录树 (user\_file)**

* **虚拟装配**：完全基于内存和关系型数据库中的虚拟节点。  
* **核心字段**：  
  * is\_dir：区分该节点是文件夹还是普通文件。  
  * file\_info\_id：若为文件夹，该字段为 NULL；若为普通文件，则指向 file\_info 物理资产表的主键。  
  * parent\_id：指向父级虚拟逻辑树节点（根目录为 0）。  
* **操作降维**：用户的重命名、文件拖拽移动、新建目录、软删除等，均仅仅作为 user\_file 虚拟表中对应字段的 UPDATE 索引变动，**磁盘物理损耗为 0**。

## **3\. 数据库物理拓扑 Schema DDL**

\-- 1\. 用户基本账户表

CREATE TABLE \`user\` (  
  \`id\` bigint NOT NULL AUTO\_INCREMENT COMMENT '主键ID',  
  \`username\` varchar(64) NOT NULL COMMENT '唯一用户名',  
  \`password\` varchar(128) NOT NULL COMMENT '加盐哈希密码密文',  
  \`salt\` varchar(64) DEFAULT NULL COMMENT '密码哈希安全盐',  
  \`role\` varchar(32) DEFAULT 'USER' COMMENT '角色权限标识(USER/ADMIN)',  
  \`create\_time\` datetime DEFAULT CURRENT\_TIMESTAMP COMMENT '注册时间戳',  
  PRIMARY KEY (\`id\`),  
  UNIQUE KEY \`uk\_username\` (\`username\`)  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci;

\-- 2\. 物理资产表（极光物理共享温区）  
CREATE TABLE \`file\_info\` (  
  \`id\` bigint NOT NULL AUTO\_INCREMENT COMMENT '物理存储映射主键',  
  \`file\_identifier\` varchar(64) NOT NULL COMMENT '文件内容全局唯一 SHA-256 安全指纹',  
  \`file\_path\` varchar(512) NOT NULL COMMENT '物理磁盘绝对存储路径',  
  \`file\_size\` bigint NOT NULL COMMENT '文件大小(单位:字节)',  
  \`file\_suffix\` varchar(32) DEFAULT NULL COMMENT '真实文件后缀名',  
  \`ref\_count\` int DEFAULT '1' COMMENT '共享池软引用计数器',  
  \`create\_time\` datetime DEFAULT CURRENT\_TIMESTAMP COMMENT '物理首次落盘时间',  
  PRIMARY KEY (\`id\`),  
  UNIQUE KEY \`uk\_identifier\` (\`file\_identifier\`)  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci;

\-- 3\. 多租户逻辑虚拟目录树表  
CREATE TABLE \`user\_file\` (  
  \`id\` bigint NOT NULL AUTO\_INCREMENT COMMENT '虚拟逻辑主键',  
  \`user\_id\` bigint NOT NULL COMMENT '租户用户主键',  
  \`file\_info\_id\` bigint DEFAULT NULL COMMENT '物理映射主键 (为文件夹时该值为Null)',  
  \`file\_name\` varchar(256) NOT NULL COMMENT '用户可视层文件名/文件夹名',  
  \`parent\_id\` bigint NOT NULL DEFAULT '0' COMMENT '父级虚拟节点ID (0代表虚拟根目录)',  
  \`is\_dir\` tinyint(1) NOT NULL DEFAULT '0' COMMENT '虚拟节点类型 (1:文件夹, 0:常规物理文件)',  
  \`deleted\` tinyint DEFAULT '0' COMMENT '软回收站标记 (1:已丢入回收站, 0:活性展示)',  
  \`create\_time\` datetime DEFAULT CURRENT\_TIMESTAMP COMMENT '节点创建时间',  
  \`update\_time\` datetime DEFAULT CURRENT\_TIMESTAMP ON UPDATE CURRENT\_TIMESTAMP COMMENT '最后活跃修改时间',  
  PRIMARY KEY (\`id\`),  
  KEY \`idx\_user\_parent\` (\`user\_id\`, \`parent\_id\`),  
  KEY \`idx\_file\_info\` (\`file\_info\_id\`)  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci;

\-- 4\. 匿名与安全临时分享控制表  
CREATE TABLE \`file\_share\` (  
  \`id\` bigint NOT NULL AUTO\_INCREMENT COMMENT '分享记录主键',  
  \`share\_code\` varchar(64) NOT NULL COMMENT '全局唯一匿名短码提取凭证(8位随机安全字符)',  
  \`user\_id\` bigint NOT NULL COMMENT '创建者用户主键',  
  \`user\_file\_id\` bigint NOT NULL COMMENT '指向被分享的逻辑节点ID',  
  \`need\_extraction\` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否开启提取密码防护 (1:是, 0:免密)',  
  \`extraction\_code\` varchar(16) DEFAULT NULL COMMENT '4位提取口令密文',  
  \`expire\_time\` datetime NOT NULL COMMENT '生命周期结束封印时间戳',  
  \`create\_time\` datetime DEFAULT CURRENT\_TIMESTAMP COMMENT '分享发起时间',  
  PRIMARY KEY (\`id\`),  
  UNIQUE KEY \`uk\_share\_code\` (\`share\_code\`)  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci;

## **4\. 核心流控机制与高并发演进算法**

### **4.1 秒传（Instant Linker）极速流控**

当客户端执行上传动作前，首先捕获文件 SHA-256 并上送。后端启动秒传大闸：

// FileServiceImpl.java 中的秒传链路探测  
FileInfo physicalFile \= fileInfoMapper.selectOne(new LambdaQueryWrapper\<FileInfo\>()  
        .eq(FileInfo::getFileIdentifier, sha256));

if (physicalFile \!= null) {  
    // 命中资产共享池：1. 物理引用计数自增 2\. 直接绑定软连接 3\. 免去物理文件流网络开销  
    physicalFile.setRefCount(physicalFile.getRefCount() \+ 1);  
    fileInfoMapper.updateById(physicalFile);  
    insertVirtualFile(userId, parentId, fileName, physicalFile.getId());  
    return; // 极速返回 200，秒传完成  
}

### **4.2 NIO 磁盘高空物理分配与分片零拷贝合并**

为了应对高并发、大文件分片合并产生的碎片化写盘锁冲突，CloudSphere 进行了 NIO 底层调优：

1. **物理空间硬核预分配**：在合并分片前，通过 RandomAccessFile.setLength() 将目标文件的物理扇区空间进行**强行预占**。这避免了系统在写入分片时频繁向 OS 申请空间扩容而造成的 I/O 阻塞：  
   try (RandomAccessFile masterRaf \= new RandomAccessFile(targetFile.toFile(), "rw")) {  
       masterRaf.setLength(totalSize); // 一步到位，硬核扩容  
   }

2. **NIO 零拷贝管道合并**：抛弃传统的 FileInputStream 到 JVM 堆内存，再从 JVM 堆写出到 FileOutputStream 的“双层二次拷贝”损耗。直接调用操作系统的 **FileChannel.transferTo()**：  
   try (FileChannel targetChannel \= FileChannel.open(targetPath, StandardOpenOption.WRITE);  
        FileChannel sourceChannel \= FileChannel.open(chunkPath, StandardOpenOption.READ)) {  
        // 利用 OS 零拷贝机制，分片流直接在 PageCache 完成拼接，CPU 拷贝损耗归零  
        sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);  
   }

### **4.3 HTTP Range 206 断点视频流推流引擎**

网盘在线播放 MP4、MKV 以及进行断点流式下载时，强依赖对客户端 Range 头的精准拦截与协议对拷。

* **状态码与头部标准**：后端拦截 Range: bytes=start-end，通过 RandomAccessFile.seek(start) 强行将磁盘指针偏移到起始流位置。向客户端返回 **HTTP 206 Partial Content**，并装配标准响应头：  
  * Accept-Ranges: bytes  
  * Content-Range: bytes {start}-{end}/{totalLength}  
  * Content-Length: {end \- start \+ 1}

### **4.4 内存安全级无限层级打包 Zip 递归下载机制**

为了实现无需在服务器磁盘生成临时压缩包、且 JVM 堆内存完全不驻留数据大实体的文件夹打包下载：

* **DFS（深度优先搜索）+ 响应流直接对灌**：通过递归向下探寻虚拟逻辑树，直接利用 ZipOutputStream 包装 HttpServletResponse.getOutputStream()。  
* **物理对拷滑动窗口**：遇到物理文件，直接以 4KB 的极其轻量滑窗流直接从物理磁盘灌注到压缩响应管道，写完立即释放物理文件输入流，将内存溢出（OOM）的风险降为绝对的 0。

### **4.5 数据库物理级“文件夹始终在文件前面”排序大闸**

为了保证在最底层的持久化层面解决文件树混杂交错的用户体验痛点：

// FileServiceImpl.java 获取子目录列表  
List\<UserFile\> userFiles \= userFileMapper.selectList(new LambdaQueryWrapper\<UserFile\>()  
        .eq(UserFile::getUserId, userId)  
        .eq(UserFile::getParentId, parentId)  
        .eq(UserFile::getDeleted, 0\)  
        // 核心一：文件夹始终置顶！ MySQL 中 Boolean 类型的 true 会映射为 1，false 映射为 0  
        .orderByDesc(UserFile::getIsDir)   
        // 核心二：同组内根据最后更新时间倒序排列，保证活跃变动资产优先展示  
        .orderByDesc(UserFile::getUpdateTime));

## **5\. 后端全局高并发踩坑与避坑实战**

在与前端（Vite \+ Vue 3）进行高并发传输与高频分享联调的过程中，后端经历了几轮极有代表性的重构调优：

1. **彻底斩断 NoResourceFoundException（404 静态资源滑落）**  
   * **病灶**：前端由于 Vite 的 proxy 代理未配置 rewrite，部分历史请求错误抛出了 POST /shares。后端 Controller 完全检索不到对应的路由，请求滑落到 Spring Boot 默认的 ResourceHttpRequestHandler 静态文件处理器中，导致抛出不雅堆栈。  
   * **整流**：后端将全量分享和匿名下载接口统一规范到 @RequestMapping("/file/share") 网关切面下，并在全局异常处理器中加入对 NoResourceFoundException 的降维降噪包装。同时，在 WebMvcConfig 中为这些合法路由正确配置匿名放行。  
2. **优雅驯服前端取消上传引发的 Tomcat EOFException**  
   * **病灶**：用户在传输大厅点击“取消上传”或关闭标签页时，前端通过 JS AbortController 刚性阻断连接。Tomcat 在持续写物理流时通道断裂，控制台瞬间爆满 EOFException。  
   * **整流**：在分片写入底层逻辑中，精准捕获 EOFException 和由于连接重置导致的 IOException。不向外抛出高危系统崩溃，而是打印 DEBUG 级简要流状态“用户已取消传输”，瞬间恢复控制台的极致纯净。  
3. **分享弹窗幽灵状态死锁拦截**  
   * **病灶**：前端由于 Vue 3 组件实例复用，导致点击第二个文件分享时未能触发后端 /file/share/create 创建新的提取码，而是复用前一次的值。  
   * **整流**：后端加强校验，每次创建调用都会在 Session 和 Redis 中生成并校验专属安全盐码，与前端保持严格的幂等性检验防爆门禁，防止前端的幽灵数据渗透。

**极光云舱，极速无界。** (CloudSphere Base Security and Storage Engine. Copyright © 2026.)