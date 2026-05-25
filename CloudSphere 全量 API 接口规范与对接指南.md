# **CloudSphere 极光网盘全量 API 接口规范与对接指南**

本规范文档定义了 **CloudSphere（极光网盘）** 系统的全量 API 契约协议，包含鉴权模块、文件与目录管理模块、直传与高性能分片上传模块、流式传输模块以及匿名临时分享安全控制模块。

## **1\. 通用协议规范**

### **1.1 基本信息**

* **协议标准**：HTTP/1.1 或 HTTP/2  
* **基础网关根路径**：http://localhost:8080 (或线上测试网关域名)  
* **默认内容编码**：Content-Type: application/json; charset=UTF-8 (文件上传与断点视频流除外)

### **1.2 鉴权门禁 (Bearer Token Token)**

除匿名白名单路径（如提取分享元信息、验证提取码、匿名流式下载）之外，所有接口请求均必须在 **HTTP 请求头** 中携带：

Authorization: Bearer \<Your\_JWT\_Token\>

### **1.3 统一数据承载结构 (ApiResponse)**

{  
  "code": 200,  
  "data": {},  
  "message": "操作成功",  
  "timestamp": 1779707501000  
}

### **1.4 全局业务状态码设计 (ResultCode)**

| 状态码 | 业务定义 | 对应 HTTP 状态码 | 说明 |
| :---- | :---- | :---- | :---- |
| 200 | SUCCESS | 200 OK | 请求成功回弹 |
| 400 | PARAM\_ERROR | 400 Bad Request | 接口入参校验失败 / 缺少必填项 |
| 401 | UNAUTHORIZED | 401 Unauthorized | Token 缺失、伪造、异常或过期 |
| 403 | FORBIDDEN | 403 Forbidden | 防越权熔断 / 高危可执行后缀名拦截 |
| 404 | FILE\_NOT\_FOUND | 404 Not Found | 逻辑文件已被回收，或物理资产丢失 |
| 500 | SYSTEM\_ERROR | 500 Internal Server Error | 后端发生内部错误 / I/O 进程被挂起 |
| 1001 | DIR\_ALREADY\_EXISTS | 200 OK (业务熔断) | 新建虚拟目录与当前活跃目录重名 |
| 1002 | DIR\_NOT\_FOUND | 200 OK (业务熔断) | 选定的父级目录主键在数据库中无对应项 |
| 2001 | SHARE\_EXPIRED | 200 OK (业务熔断) | 匿名分享链接到期，生命周期封印 |
| 2002 | SHARE\_CODE\_ERROR | 200 OK (业务熔断) | 匿名分享提取口令不匹配，拦截暴刷 |

## **2\. API 接口规范**

### **2.1 用户鉴权模块 (UserController)**

#### **1\) 租户登录认证与安全 Token 核发**

* **请求路径**：POST /user/login  
* **Content-Type**：application/json  
* **请求体 (UserLoginDTO)**：  
  {  
    "username": "admin",  
    "password": "yourpassword123"  
  }

* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsImV4cCI6MTc3OTcwNzUwMX0...",  
    "message": "登录成功",  
    "timestamp": 1779707501000  
  }

### **2.2 文件与目录管理模块 (FileController)**

#### **1\) 获取当前子目录下的虚拟文件树列表**

* **请求路径**：GET /file/list  
* **请求参数 (Query)**：

| 参数名 | 类型 | 是否必填 | 默认值 | 说明 |
| :---- | :---- | :---- | :---- | :---- |
| parentId | Long | 是 | 0 | 父级目录虚拟节点主键 (0代表虚拟根目录) |

* **核心排序规范**：**文件夹绝对置顶在最上方，常规物理文件排在下方；且各自区间内按照最后修改时间倒序排列**。  
* **返回数据 (ApiResponse\<List\>)**：  
  {  
    "code": 200,  
    "data": \[  
      {  
        "id": 12,  
        "name": "极光微内核开发文档",  
        "folder": true,  
        "updateTime": "2026-05-25 18:22:45",  
        "size": null,  
        "sha256": null  
      },  
      {  
        "id": 15,  
        "name": "cloudsphere-core.jar",  
        "folder": false,  
        "updateTime": "2026-05-25 19:15:30",  
        "size": 52428800,  
        "sha256": "4a5b6c7d8e9f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f"  
      }  
    \],  
    "message": "操作成功",  
    "timestamp": 1779707501000  
  }

#### **2\) 新建虚拟文件夹**

* **请求路径**：POST /file/createFolder  
* **Content-Type**：application/json  
* **请求体 (FolderCreateDTO)**：  
  {  
    "name": "核心研发代码温区",  
    "parentId": 12  
  }

* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": null,  
    "message": "操作成功",  
    "timestamp": 1779707501000  
  }

#### **3\) 物理解绑软删除（逻辑节点删除）**

* **请求路径**：POST /file/deleteFile/{fileId}  
* **请求参数 (Path)**：

| 参数名 | 类型 | 是否必填 | 说明 |
| :---- | :---- | :---- | :---- |
| fileId | Long | 是 | 需要解约的逻辑节点 ID |

* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": null,  
    "message": "操作成功",  
    "timestamp": 1779707501000  
  }

### **2.3 物理直传/高性能分片上传模块 (ChunkUploadController & FileController)**

#### **1\) 单文件物理直传 / 秒传秒级探测防爆接口**

* **请求路径**：POST /file/upload  
* **Content-Type**：multipart/form-data  
* **请求体 (FormData)**：

| 表单 Key | 类型 | 说明 |
| :---- | :---- | :---- |
| file | File | 物理二进制大文件文件实体 |
| sha256 | String | 当前大文件的全局哈希指纹 |
| parentId | Long | 逻辑目标父级 ID |
| fileName | String | 用户保存的文件名 |

* **流控特征**：若哈希命中资产共享池，**无需任何磁盘写，服务器无网络传流，物理 I/O \= 0，1ms 内秒级秒传放行**。  
* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": null,  
    "message": "操作成功",  
    "timestamp": 1779707501000  
  }

#### **2\) 分片上传前置初始化与续传断点探测**

* **请求路径**：POST /file/chunk/init  
* **Content-Type**：application/json  
* **请求体 (ChunkInitDTO)**：  
  {  
    "identifier": "b4f2c8d7e91a5c2d1b8e9f2a3c4d5e6f7a8b9c0d1e2f3a4b",  
    "fileName": "avatar\_pack.zip",  
    "totalChunks": 24,  
    "totalSize": 251658240  
  }

* **返回数据 (ApiResponse\<List\>)**：  
  {  
    "code": 200,  
    "data": \[1, 2, 3, 4\],   
    "message": "探测完成，已为你恢复传输视轨",  
    "timestamp": 1779707501000  
  }

  *(注: data 数组中返回的是已经安全落盘在分片临时温区的分片编号。前端在收到响应后，只需多线程并发补齐剩下没发的片，实现秒级断点断传)*

#### **3\) 并发分片物理灌流写入**

* **请求路径**：POST /file/chunk/upload  
* **Content-Type**：multipart/form-data  
* **请求体 (FormData)**：

| 表单 Key | 类型 | 说明 |
| :---- | :---- | :---- |
| file | File | 当前切片分段的物理二进制流文件实体 |
| identifier | String | 整个大文件的全局唯一 SHA-256 哈希值 |
| chunkNumber | Integer | 当前分段的编号索引 (1-indexed) |

* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": null,  
    "message": "切片落盘就绪",  
    "timestamp": 1779707501000  
  }

#### **4\) 分片物理合并与 NIO 零拷贝封缄大闸**

* **请求路径**：POST /file/chunk/merge  
* **Content-Type**：application/json  
* **请求体 (FileMergeDTO)**：  
  {  
    "identifier": "b4f2c8d7e91a5c2d1b8e9f2a3c4d5e6f7a8b9c0d1e2f3a4b",  
    "fileName": "avatar\_pack.zip",  
    "parentId": 0  
  }

* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": null,  
    "message": "大文件物理合并完成，已正式入驻虚拟目录",  
    "timestamp": 1779707501000  
  }

### **2.4 资源下载与高性能推流模块 (FileController)**

#### **1\) 逻辑文件 HTTP Range 206 断点流式推流/大文件下载**

* **请求路径**：GET /file/download/{fileId}  
* **请求参数 (Path)**：

| 参数名 | 类型 | 是否必填 | 说明 |
| :---- | :---- | :---- | :---- |
| fileId | Long | 是 | 需要拉取下载的虚拟逻辑文件主键 |

* **核心 HTTP 响应头适配**：  
  * **206 状态码拦截响应头**：  
    * Accept-Ranges: bytes  
    * Content-Range: bytes {start}-{end}/{totalLength}  
    * Content-Length: {contentLength}  
  * **200 正常大文件下载响应头**：  
    * Content-Type: application/octet-stream (针对普通文本自动追加：text/markdown; charset=UTF-8，防止放映厅阅览乱码)  
    * Content-Disposition: inline; filename="{encoded\_utf8\_filename}"

#### **2\) 虚拟层级目录树流式打包 Zip 导出**

* **请求路径**：GET /file/download/folder/{folderId}  
* **请求参数 (Path)**：

| 参数名 | 类型 | 是否必填 | 说明 |
| :---- | :---- | :---- | :---- |
| folderId | Long | 是 | 虚拟目标文件夹逻辑主键 |

* **响应 Content-Type**：application/zip

### **2.5 安全分享控制模块 (ShareController)**

#### **1\) 创建临时匿名提取分享**

* **请求路径**：POST /file/share/create  
* **Content-Type**：application/json  
* **请求体 (ShareCreateDTO)**：  
  {  
    "fileId": 15,  
    "expireTime": "2026-06-01 12:00:00",  
    "needExtraction": true  
  }

* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": {  
      "shareCode": "A7z9Kd8X",  
      "extractionCode": "8472",  
      "expireTime": "2026-06-01 12:00:00"  
    },  
    "message": "分享创建成功",  
    "timestamp": 1779707501000  
  }

#### **2\) 提取分享元信息（匿名免签放行）**

* **请求路径**：GET /file/share/info/{shareCode}  
* **请求参数 (Path)**：shareCode (8位分享提取码短码)  
* **拦截器放行规范**：已在 WebMvcConfig 中配置拦截排除，此接口无需携带 Authorization 头，可跨域跨物理客户端匿名直接拉取。  
* **返回数据 (ApiResponse)**：  
  {  
    "code": 200,  
    "data": {  
      "fileName": "cloudsphere-core.jar",  
      "fileSize": 52428800,  
      "username": "极光创始租户",  
      "needExtraction": true  
    },  
    "message": "操作成功",  
    "timestamp": 1779707501000  
  }

#### **3\) 提取口令核验与临时 Ticket 授予**

* **请求路径**：POST /file/share/verify  
* **请求参数 (Query)**：

| 参数名 | 类型 | 是否必填 | 说明 |
| :---- | :---- | :---- | :---- |
| shareCode | String | 是 | 8位匿名分享提取短码 |
| extractionCode | String | 是 | 4位提取口令密文 |

* **返回数据 (ApiResponse)**：若口令匹配正确，服务器回弹验证成功状态。

**极光云舱，极速无界。** (CloudSphere Base Security and Storage Engine. Copyright © 2026.)