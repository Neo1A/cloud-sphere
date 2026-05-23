# **极光网盘（CloudSphere）核心系统设计与研发现状蓝皮书**

本手册作为极光网盘（CloudSphere）系统的核心架构与研发进度交班白皮书，旨在明确网盘底层高性能 I/O 流控的设计模型，并清晰划分当前系统的**已测通边界**、**部分实现模块**与**暂未编码的预留空白区**。

## **一、 系统工程架构与研发现状大盘**

本系统在物理结构上采用 Spring Boot 3.x 核心骨架，结合 MyBatis-Plus 3.5 实现了多租户“逻辑虚拟目录树”与“物理资产共享池”的完全解耦。

### **1.1 研发状态图例说明**

* \[🟢 已全线测通\]：代表该模块/文件的核心业务代码已通过 1GB 大文件极限压测，功能在本地 Windows 与玩客云宿主机间合龙，表现极其平稳。  
* \[🟡 基础脚手架/部分实现\]：代表该层仅拥有空置脚手架、基础拦截器或已建实体类，部分高级特性（如空间超限拦截、物理关联删除）尚未编码覆盖。  
* \[🚧 预留设计/暂未实现\]：代表仅在系统目录中建立了包文件夹、占位文件或数据库表设计，尚未进行任何实际业务逻辑与 Controller 路由实现。

### **1.2 工程物理目录树与状态对齐表**

C:.  
├───.idea  
│   └───dataSources              \# \[🟢 已全线测通\] IDEA 本地 storage\_v2 数据库连接快照  
├───src  
│   ├───main  
│   │   ├───java  
│   │   │   └───com.cloudsphere.netdisk  
│   │   │       ├───common              \# 全局通用基础设施层 \[🟡 部分实现\]  
│   │   │       │   ├───annotation      \# 自定义注解  
│   │   │       │   │   ├─── @ExcludeAuth   \[🟢 已全线测通\] 匿名免密放行注解  
│   │   │       │   │   └─── @Idempotent    \[🚧 预留设计\] 分片并发合并幂等 AOP 注解  
│   │   │       │   ├───api             \# 统一响应封装 \[🟢 已全线测通\] (ApiResponse, ResultCode)  
│   │   │       │   ├───aspect          \# 切面拦截  
│   │   │       │   │   ├─── LogAspect      \[🟢 已全线测通\] 全局方法性能追踪日志切面  
│   │   │       │   │   └─── LockAspect     \[🚧 预留设计\] 用于高并发分片追加合并的分布式锁切面  
│   │   │       │   ├───constant        \# 核心常量池 \[🟢 已全线测通\] (Redis 缓存前缀等)  
│   │   │       │   ├───exception       \# 统一异常体系 \[🟢 已全线测通\] (BusinessException)  
│   │   │       │   └───utils           \# 核心底层工具 \[🟢 已全线测通\] (UserContext 租户提取、JWT 校验)  
│   │   │       │  
│   │   │       ├───config              \# 核心配置注入层 \[🟢 已全线测通\] (WebMvcConfig 拦截器配置)  
│   │   │       │  
│   │   │       ├───interceptor         \# 拦截器层 \[🟢 已全线测通\] (JwtInterceptor 安全凭证解析)  
│   │   │       │  
│   │   │       ├───controller          \# 核心路由网关层 \[🟡 部分实现\]  
│   │   │       │   ├─── FileController.java        \# \[🟢 已全线测通\] 收拢直传、秒传、分片合并、HTTP Range 206 下载  
│   │   │       │   ├─── UserController.java        \# \[🟡 脚手架已打通\] 用户登录、注册。用户个人空间核验及注销暂未实现  
│   │   │       │   └─── ShareController.java       \# \[🚧 预留设计\] 匿名外发分享提取控制器，目前尚未编写  
│   │   │       │  
│   │   │       ├───dto                 \# 数据传输对象层 \[🟡 部分实现\]  
│   │   │       │   ├─── FolderCreateDTO.java       \# \[🟢 已全线测通\] 虚拟目录建档入参  
│   │   │       │   ├─── UploadInitDTO.java         \# \[🟢 已全线测通\] 物理文件初始化入参  
│   │   │       │   ├─── FileMergeDTO.java          \# \[🟢 已全线测通\] 大文件 NIO 合并入参  
│   │   │       │   └─── ShareCreateDTO.java        \# \[🚧 预留设计\] 链接分享创建入参，尚未编写  
│   │   │       │  
│   │   │       ├───entity              \# 数据库 ORM 实体层 \[🟡 部分实现\]  
│   │   │       │   ├─── FileInfo.java              \# \[🟢 已全线测通\] 物理资产实体类  
│   │   │       │   ├─── UserFile.java              \# \[🟢 已全线测通\] 虚拟逻辑树实体类  
│   │   │       │   ├─── User.java                  \# \[🟡 部分实现\] 基础用户字段，totalSpace 拦截字段暂未编码关联  
│   │   │       │   ├─── FileChunk.java             \# \[🚧 预留设计\] 刚性限流物理碎片记录，后端实体尚未编写  
│   │   │       │   └─── FileShare.java             \# \[🚧 预留设计\] 提取码分享实体类，后端实体尚未编写  
│   │   │       │  
│   │   │       ├───mapper              \# 数据访问持久层 \[🟡 部分实现\]  
│   │   │       │   ├─── FileInfoMapper.java        \# \[🟢 已全线测通\] 物理载荷 CRUD 映射器  
│   │   │       │   ├─── UserFileMapper.java        \# \[🟢 已全线测通\] 虚拟树逻辑操作映射器  
│   │   │       │   ├─── UserMapper.java            \# \[🟢 已全线测通\] 租户信息基础持久化映射器  
│   │   │       │   ├─── FileChunkMapper.java       \# \[🚧 预留设计\] 分片备份持久化映射，尚未编写  
│   │   │       │   └─── FileShareMapper.java       \# \[🚧 预留设计\] 链接分享映射器，尚未编写  
│   │   │       │  
│   │   │       └───service             \# 核心流控业务层 \[🟡 部分实现\]  
│   │   │           ├─── FileService.java               \# 核心流控下载、列表所有权核验定义 \[🟢 已全线测通\]  
│   │   │           ├─── ChunkUploadService.java        \# Redis 状态打卡、NIO 零拷贝合并定义 \[🟢 已全线测通\]  
│   │   │           └─── impl  
│   │   │               ├─── FileServiceImpl.java       \# \[🟢 已全线测通\] 常规直传、直传秒传、Range 206 视频在线下载引擎  
│   │   │               │                               \# (🚧 暂未实现：deleteFile 里的“引用计数归零、外置盘物理抹除”逻辑)  
│   │   │               └─── ChunkUploadServiceImpl.java  \# \[🟢 已全线测通\] NIO transferTo 零拷贝碎块捏合及 Redis 同步粉碎机制  
│   │   │  
│   │   └───resources  
│   │       ├───static                  \# 静态资源暂存夹 \[🚧 预留设计\] 预留 Vue3 单页 dist 静态合龙空间  
│   │       ├───templates               \# 模板引擎夹 \[🚧 预留设计\]  
│   │       └───application.yml         \# 核心配置文件 \[🟢 已全线测通\] (动态环境 storageRoot、Tomcat 大容量直传拦截放行)

## **二、 全量数据库物理 Schema 与研发落盘状态**

本章节包含了目前极光网盘底座所需的**全部物理表 SQL DDL 脚本**。为了保证后续功能的顺畅演进，未实现业务的表结构已在此进行物理建表设计，并在小节标题处予以明确声明。

\-- \=====================================================================  
\-- 1\. 全局环境与控制台建库大印 \[🟢 已全线测通\]  
\-- \=====================================================================  
CREATE DATABASE IF NOT EXISTS \`cloudsphere\_netdisk\`   
    DEFAULT CHARACTER SET utf8mb4   
    COLLATE utf8mb4\_general\_ci;

USE \`cloudsphere\_netdisk\`;

\-- 刚性安全整流：强行关闭外键级联检查，保障高并发批量物理粉碎时的绝对吞吐率  
SET FOREIGN\_KEY\_CHECKS \= 0;

\-- \=====================================================================  
\-- 2\. 租户用户安全凭证表：user \[🟡 脚手架已通 · 容量核验暂未编码\]  
\-- \=====================================================================  
DROP TABLE IF EXISTS \`user\`;  
CREATE TABLE \`user\` (  
  \`id\` bigint(20) NOT NULL AUTO\_INCREMENT COMMENT '租户用户自增主键',  
  \`username\` varchar(64) NOT NULL COMMENT '唯一登录账户名',  
  \`password\` varchar(128) NOT NULL COMMENT '加盐加密密文密码',  
  \`salt\` varchar(64) DEFAULT NULL COMMENT '非对称加盐混淆因子',  
  \`status\` tinyint(4) NOT NULL DEFAULT '1' COMMENT '账号状态(1-正常活跃, 0-冻结禁封)',  
  \`total\_space\` bigint(20) NOT NULL DEFAULT '16106127360' COMMENT '网盘分配容量上限(字节, 默认15GB) \[🚧 暂未实现拦截\]',  
  \`used\_space\` bigint(20) NOT NULL DEFAULT '0' COMMENT '当前已占用的物理体积(字节) \[🚧 暂未实现拦截\]',  
  \`create\_time\` datetime NOT NULL DEFAULT CURRENT\_TIMESTAMP COMMENT '注册打卡时间',  
  \`update\_time\` datetime NOT NULL DEFAULT CURRENT\_TIMESTAMP ON UPDATE CURRENT\_TIMESTAMP COMMENT '资料变更时间',  
  PRIMARY KEY (\`id\`),  
  UNIQUE KEY \`uk\_username\` (\`username\`) USING BTREE  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci COMMENT='租户用户安全认证主表';

\-- \=====================================================================  
\-- 3\. 物理资产温区共享池：file\_info \[🟢 已全线测通\]  
\-- \=====================================================================  
DROP TABLE IF EXISTS \`file\_info\`;  
CREATE TABLE \`file\_info\` (  
  \`id\` bigint(20) NOT NULL AUTO\_INCREMENT COMMENT '物理实体资产自增主键',  
  \`file\_identifier\` varchar(64) NOT NULL COMMENT '文件全局唯一 SHA-256 哈希印记(秒传核心)',  
  \`file\_path\` varchar(512) NOT NULL COMMENT '本地磁盘/外置存储绝对物理存储路径',  
  \`file\_size\` bigint(20) NOT NULL COMMENT '文件真实物理体积大小(字节 Byte)',  
  \`file\_suffix\` varchar(20) DEFAULT NULL COMMENT '文件真实物理后缀名(如 .img, .mp4)',  
  \`ref\_count\` int(11) NOT NULL DEFAULT '1' COMMENT '多租户共享引用计数器(归零时实施物理删除)',  
  \`create\_time\` datetime NOT NULL DEFAULT CURRENT\_TIMESTAMP COMMENT '首次物理载荷落盘时间',  
  PRIMARY KEY (\`id\`),  
  UNIQUE KEY \`uk\_file\_identifier\` (\`file\_identifier\`) USING BTREE  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci COMMENT='物理资产仓实体表';

\-- \=====================================================================  
\-- 4\. 逻辑用户虚拟目录树表：user\_file \[🟢 已全线测通\]  
\-- \=====================================================================  
DROP TABLE IF EXISTS \`user\_file\`;  
CREATE TABLE \`user\_file\` (  
  \`id\` bigint(20) NOT NULL AUTO\_INCREMENT COMMENT '逻辑虚拟树节点自增主键',  
  \`user\_id\` bigint(20) NOT NULL COMMENT '所属租户用户 ID',  
  \`parent\_id\` bigint(20) NOT NULL DEFAULT '0' COMMENT '父级虚拟目录主键 ID (顶层根目录恒为 0)',  
  \`file\_info\_id\` bigint(20) DEFAULT NULL COMMENT '关联的物理实体资产 ID (若当前节点为文件夹则留空)',  
  \`file\_name\` varchar(256) NOT NULL COMMENT '租户逻辑展现文件名/文件夹名',  
  \`is\_dir\` tinyint(1) NOT NULL DEFAULT '0' COMMENT '虚拟节点属性(1-文件夹, 0-普通文件)',  
  \`deleted\` tinyint(1) NOT NULL DEFAULT '0' COMMENT '回收站软删除标记(1-已删除, 0-长驻可见)',  
  \`create\_time\` datetime NOT NULL DEFAULT CURRENT\_TIMESTAMP COMMENT '虚拟节点创建时间',  
  \`update\_time\` datetime NOT NULL DEFAULT CURRENT\_TIMESTAMP ON UPDATE CURRENT\_TIMESTAMP COMMENT '树形节点最近变更时间',  
  PRIMARY KEY (\`id\`),  
  KEY \`idx\_user\_parent\` (\`user\_id\`,\`parent\_id\`) USING BTREE,  
  KEY \`idx\_file\_info\` (\`file\_info\_id\`) USING BTREE  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci COMMENT='逻辑用户虚拟文件树表';

\-- \=====================================================================  
\-- 5\. 刚性限流分片落盘事务中转表：file\_chunk \[🚧 仅设计DDL · 后端无此代码\]  
\-- \=====================================================================  
\-- 💡 说明：目前分片上传状态完全保存在 Redis 7.0 极速缓存中，并已完全测通。  
\-- 该物理表作为预留的“宿主机突发断电、缓存持久化防丢双重保险表”，目前暂无任何后端代码连接或写入。  
DROP TABLE IF EXISTS \`file\_chunk\`;  
CREATE TABLE \`file\_chunk\` (  
  \`id\` bigint(20) NOT NULL AUTO\_INCREMENT COMMENT '分片事务自增主键',  
  \`identifier\` varchar(64) NOT NULL COMMENT '所属大文件的全局唯一 SHA-256 哈希印记',  
  \`chunk\_number\` int(11) NOT NULL COMMENT '当前分片的位置编号(自 1 开始)',  
  \`chunk\_size\` bigint(20) NOT NULL COMMENT '当前碎片载荷的真实体积(字节)',  
  \`chunk\_path\` varchar(512) NOT NULL COMMENT '碎片在本地临时温区的绝对路径',  
  \`create\_time\` datetime NOT NULL DEFAULT CURRENT\_TIMESTAMP COMMENT '碎片落盘存储时间',  
  PRIMARY KEY (\`id\`),  
  UNIQUE KEY \`uk\_identifier\_chunk\` (\`identifier\`,\`chunk\_number\`) USING BTREE  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci COMMENT='大文件分片临时存储中转表';

\-- \=====================================================================  
\-- 6\. 安全时效性私密分享控制表：file\_share \[🚧 仅设计DDL · 后端无此代码\]  
\-- \=====================================================================  
\-- 💡 说明：本表用于后续“第六阶段”生成凭四位提取码匿名提取、自动过期销毁的分享功能。  
\-- 目前纯属于数据库预留，后端尚未编写任何对应的 Controller 路由及 Service 提取代码。  
DROP TABLE IF EXISTS \`file\_share\`;  
CREATE TABLE \`file\_share\` (  
  \`id\` bigint(20) NOT NULL AUTO\_INCREMENT COMMENT '分享条目自增主键',  
  \`share\_code\` varchar(32) NOT NULL COMMENT '提取链接短路由加密令牌 (如 A6B9)',  
  \`user\_id\` bigint(20) NOT NULL COMMENT '发起分享的源头租户 ID',  
  \`user\_file\_id\` bigint(20) NOT NULL COMMENT '被分享的逻辑树节点 ID',  
  \`share\_type\` tinyint(4) NOT NULL DEFAULT '0' COMMENT '分享类型(0-凭提取码私密分享, 1-公开无密分享)',  
  \`password\` varchar(10) DEFAULT NULL COMMENT '4位提取码密匙明文',  
  \`valid\_days\` int(11) NOT NULL DEFAULT '7' COMMENT '有效时限天数(0代表永久有效)',  
  \`expire\_time\` datetime NOT NULL COMMENT '绝对失效临界时间点',  
  \`click\_count\` int(11) NOT NULL DEFAULT '0' COMMENT '分享链接转存/点击热度计数器',  
  \`create\_time\` datetime NOT NULL DEFAULT CURRENT\_TIMESTAMP COMMENT '分享创建时间',  
  PRIMARY KEY (\`id\`),  
  UNIQUE KEY \`uk\_share\_code\` (\`share\_code\`) USING BTREE,  
  KEY \`idx\_user\_share\` (\`user\_id\`) USING BTREE  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4\_general\_ci COMMENT='安全时效性私密分享控制表';

\-- 重新开启全局外键约束审计大闸  
SET FOREIGN\_KEY\_CHECKS \= 1;

## **三、 API 接口设计与开发完成度对账大盘**

全域 API 请求头必须携带标准 JWT 身份令牌：Authorization: Bearer {jwt\_token}。

### **3.1 🟢 核心已测通上线接口矩阵**

以下接口在最新版的全功能网关 FileController.java 中已完全编写就绪，并通过 1GB 大文件在多操作系统下的测试：

#### **1\. 常规单文件物理直传 / 直传秒传防爆大闸**

* **接口路径**：POST /file/upload  
* **Content-Type**：multipart/form-data  
* **请求体 (Form-Data)**：  
  * file (物理二进制流)、sha256 (64位文件哈希)、parentId (虚拟父级目录ID)、fileName (逻辑显示名称)  
* **秒传流控行为**：若哈希命中 file\_info 物理表，零磁盘 I/O 损耗，瞬间增加计数，复用物理实体，秒级响应 200。

#### **2\. 获取当前目录下文件列表**

* **接口路径**：GET /file/list?parentId={id}  
* **安全防越权**：自动捕获租户安全 UserContext。若用户企图横向越权碰撞非归属的逻辑节点，拦截器与 Service 直接拒绝并熔断返回 403 FORBIDDEN。

#### **3\. 智能断点分片初始化探测**

* **接口路径**：POST /file/chunk/init (Content-Type: application/json)  
* **续传状态召回**：后端直接从 Redis 召回已成功打卡的分片小编号集合。前端据此实施刚性剪裁，跳过已存分片并自动呼叫后续上传（高容错续传核心）。

#### **4\. 精细自适应分片上传**

* **接口路径**：POST /file/chunk/upload (Content-Type: multipart/form-data)  
* **蚂蚁搬家控流**：单个碎片体积硬卡 ![][image1] 级别。安全穿透 Nginx 或宿主机 Tomcat 默认网闸。

#### **5\. NIO 内核级零拷贝追加物理合并**

* **接口路径**：POST /file/chunk/merge (Content-Type: application/json)  
* **零 OOM 合龙**：利用 FileChannel.transferTo()。合并完成后自动触发扫尾：同步销毁 temp 温区下多达几千个物理碎片工作包，同步抹除 Redis 缓存。

#### **6\. 高性能 HTTP Range 206 块级断点下载 / 在线多媒体视频播放**

* **接口路径**：GET /file/download/{fileId}  
* **多媒体在线预览特性**：在 HTTP Header 中注入 Content-Disposition: inline。支持 Safari、Chrome 等浏览器直接内嵌播放，通过 Range: bytes=start-end 计算随机指针，支持视频任意秒开和进度条快进/快退。

### **3.2 🚧 暂未实现 / 预留设计的空白接口组**

以下接口目前在后端源码中**完全处于空白状态，尚未进行代码设计与接口编码**：

#### **1\. 用户登出注销接口**

* **预留路径**：POST /user/logout  
* **当前现状**：尚未开始编写。目前完全依赖前端客户端主动销毁本地 LocalStorage 中的 JWT 令牌，后端暂未引入 Redis 黑名单吊销大闸。

#### **2\. 网盘虚拟文件删除（带物理联动删除）**

* **预留路径**：POST /file/delete  
* **当前现状**：deleteFile 逻辑目前仅实现了 user\_file 虚拟目录树的逻辑软解绑、以及物理引用计数 ref\_count 的递减。当计数归零时顺藤摸瓜将磁盘里的实体文件执行物理连带粉碎抹除（Files.deleteIfExists）的核心逻辑**目前在宿主机上尚属受控未联调阶段，暂时未将其对外暴露为可供前端呼叫的 API Controller 路由**。

#### **3\. 一键生成带有时效提取码的分享链接**

* **预留路径**：POST /file/share/create  
* **当前现状**：尚未实现。由于高级 file\_share 业务层尚未编写，目前没有此接口，无法生成 4 位提取码及生成分享短链。

#### **4\. 匿名外发下载/凭提取码转存分享数据**

* **预留路径**：GET /file/share/extract  
* **当前现状**：尚未实现。未来高级阶段用于匿名游客通过凭提取码绕过身份认证直接定向抠取物理下载流，目前完全空白。

## **四、 工业级 Docker 依赖环境一键化部署**

为了保障本地开发机（10.1.1.11）与远端低功耗 Linux（10.1.1.100 玩客云/软路由）生产环境的数据流控行为高度一致，所有基础依赖一律使用 Docker 容器化隔离。

### **4.1 容器群网络与存储编排 docker-compose.yml**

请在宿主机任意空目录下创建该编排文件：

version: '3.8'

services:  
  \# 🎯 核心关系型数据库：MySQL 8.0 高吞吐调优版  
  cloudsphere-mysql:  
    image: mysql:8.0  
    container\_name: cloudsphere-mysql  
    restart: always  
    environment:  
      MYSQL\_ROOT\_PASSWORD: root\_secure\_password  
      MYSQL\_DATABASE: cloudsphere\_netdisk  
      TZ: Asia/Shanghai  
    ports:  
      \- "3306:3306"  
    volumes:  
      \- ./mysql/data:/var/lib/mysql  
      \- ./mysql/conf/my.cnf:/etc/mysql/conf.d/my.cnf  
      \- ./mysql/init:/docker-entrypoint-initdb.d \# 自动化零介入建表挂载点  
    command:  
      \--default-authentication-plugin=mysql\_native\_password  
      \--character-set-server=utf8mb4  
      \--collation-server=utf8mb4\_general\_ci  
      \--max\_connections=1000  
      \--innodb\_buffer\_pool\_size=512M \# 为低功耗 Arm 开发板特别优化的物理内存缓存大闸  
    networks:  
      cloudsphere-net:  
        ipv4\_address: 172.50.0.10

  \# 🎯 高速缓存与分片打卡器：Redis 7.0 内存安全版  
  cloudsphere-redis:  
    image: redis:7.0-alpine  
    container\_name: cloudsphere-redis  
    restart: always  
    ports:  
      \- "6379:6379"  
    volumes:  
      \- ./redis/data:/data  
    command:   
      redis-server \--requirepass redis\_secure\_password \--appendonly yes \--maxmemory 256mb \--maxmemory-policy allkeys-lru  
    networks:  
      cloudsphere-net:  
        ipv4\_address: 172.50.0.20

networks:  
  cloudsphere-net:  
    driver: bridge  
    ipam:  
      config:  
        \- subnet: 172.50.0.0/16

### **4.2 零手工介入的自动化建表配置步骤**

1. 在 docker-compose.yml 同级目录下，在宿主机执行命令：  
   mkdir \-p mysql/init  
2. 在该目录下新建空文件 schema.sql，并将本手册 **第二章节（数据库拓扑 DDL）** 的全部 SQL 语句完整粘贴进去。  
3. 执行一键后台拉起命令：  
   docker compose up \-d  
4. 容器群首次启动时，MySQL 自动加载并执行 schema.sql 完成物理空库建表，整个极光网盘的底层环境即刻进入随时可用状态。

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEMAAAAZCAYAAABq35PiAAAD1UlEQVR4Xu1XS2gUQRDdJQoRRVGJS5Ld7d3NouQgBtYP8eBFUUQiooKi3kSJ4EE8KOaiIjkkogcFDUEUBQ9+0JMngwYF/6AHf/hBDMFgRHOJgsFV38t0TyqVmewqnmQeFD39qrqruvo7sViECBEiRIjwT5ABjDGbamtrk6hWoFqZzWbnpdPp7fyWtuDnwLYd0sk2yWRyktQT5KizNu1so20k4KMZdoOQX0L64X+Zs0H9lNRD12H5o5Ah1fYL5IP9/oj+2/L5/NQRj+MADRZDvqsOv0JWKbt1kOfovKGqqmoKvg9BrudyuWnOht/kqKMNbdmGbWVfQYBNi/W9Wes4UeBfIwnbCoXCRKWOQ3eeMUNfkArjjW0A/CtMipG6QLADG/BLyBMM4CDKamlTU1OTYjAyULSbjvpDyE7B7SVHnePYBvICwSQcFwTblrPeJHmb4MuQtZKXQMxnTUAyCPCt7Bc2B7RuDGwyjmtewg5IO3Mz0s1V4JLDwIRNLJVKLQA/iHK15DWCksFEoL8LRq1SjfGS4frVcQWizGQcC3Jmg+gDn0NZD/msndr+ue1aJa+hk2FXxJVSiSDCksEtBd1F6IqlJmMYNtirkHOQN6j3QPbLwzHMmeTdoMOSoXkNmQz4noHvG5C3aJfRtho2jm8oV6CspvCMAE5ABsBvgFlctxsDO5D7CCDPOg6rmWj8AJ10MrP2sOwuIxlNHIwe9J8mA3LEeJNziXXwZwIOzVGwcfy07XiLOXkEOV3qRvNBR4lEYrLkjHey84ZppA5llymRDJQrGbwe9F8k4xOW9Hy7Tei3aErcRjIOyXNsxktKETY7pK5siMB2sx7mTK2MwEGH8RrCp39rYEYXmjKuxrD4CGPPMkgvJKv1PrgljHc9Pq2rq5vleBcYS9aNdz2NcWaD6OU7IO0don160C4ZkBbJawif8mqNo7995I3dtkLno0QyeIa8D9P7EIajksHAGYA7gVmiXkyLVyECqAR3jcJvcbYM150d24Abkm2DEJIMd73eon8Tsl1KJKPReFt+/JUBTEAHHehskSOE8y73unSHakY8XHjg0gG4jY5DX1sgPcJp3Hgv1XvypRoEM/ICHd6aEuhzKfgf6ZDtEpYMrlg7Fp5lu6QuEPYKug05DNkKeQy5Y/9VfPBQA/8ODvdA1hvvgdUmly6/oTsJ3U3o1hgvEc/w3SD7ksh4/yb9DFiI/2+CpM/Vek4MV7Lx/k34L6LbckLc/85djHFJrJyrleAg2MAOsh5UhbYheLMgKcs5UD7Rtd4ijlUwm32xz7B9HiFChAgR/hP8BphhliBfg830AAAAAElFTkSuQmCC>