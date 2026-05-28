-- ============================================================
-- 矿业集团企业网盘系统 - 数据库初始化脚本（去重修复版）
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. 科室部门表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '部门/科室自增主键ID',
`dept_name` varchar(128) NOT NULL COMMENT '科室名称(如：采掘科、安监部、机运科)',
`parent_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '父级部门ID(支持无限层级科层，根部门为0)',
PRIMARY KEY (`id`),
KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业组织架构-部门科室表';

-- ------------------------------------------------------------
-- 2. 副矿长分管科室关联表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `dept_vice_director_relation`;
CREATE TABLE `dept_vice_director_relation` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键ID',
`vice_director_user_id` bigint(20) NOT NULL COMMENT '副矿长的用户ID(对应user.id)',
`dept_id` bigint(20) NOT NULL COMMENT '其分管的科室部门ID(对应department.id)',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_director_dept` (`vice_director_user_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='副矿长分管科室多对多关联表';

-- ------------------------------------------------------------
-- 3. 存储库空间表（公共/部门两级，已废弃个人库，修复 tinyint 类型）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `storage_repository`;
CREATE TABLE `storage_repository` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '存储库空间唯一自增主键ID',
`repo_name` varchar(128) NOT NULL COMMENT '存储库空间展现名(如：全矿安全标准公盘、机运科私享协作库)',
`repo_type` tinyint NOT NULL DEFAULT 2 COMMENT '存储库制式类型: 1-公共存储库(全员准入/受控修改), 2-部门存储库(科室行政专属隔离区)',
`dept_id` bigint(20) DEFAULT NULL COMMENT '关联所属部门科室ID。若 repo_type=1 则此字段恒为 NULL；若 repo_type=2 则不可为空',
`status` tinyint NOT NULL DEFAULT 1 COMMENT '存储库活性状态: 1-正常启用, 0-封存/禁用锁死',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '空间开辟与数字化建档时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '空间配置元数据最近变更时间',
PRIMARY KEY (`id`),
KEY `idx_dept_id` (`dept_id`),
KEY `idx_type_status` (`repo_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业协同温区-公共/部门多模协作存储库空间表';

-- ------------------------------------------------------------
-- 4. 用户员工认证表（升级版，补充状态字段与默认值，修复类型）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '全局唯一用户ID',
`username` varchar(50) NOT NULL COMMENT '工号/用户名',
`password` varchar(64) NOT NULL COMMENT 'SHA-256加盐哈希密码',
`real_name` varchar(64) NOT NULL COMMENT '员工真实姓名',
`dept_id` bigint(20) DEFAULT NULL COMMENT '所属科室ID(外键关联department.id)',
`role` varchar(32) NOT NULL DEFAULT 'USER' COMMENT '角色权限: ADMIN(管理员), MINER_DIRECTOR(矿长), VICE_DIRECTOR(副矿长), SECTION_CHIEF(科长), USER(常规个人)',
`status` tinyint NOT NULL DEFAULT 1 COMMENT '账户状态: 1-正常在职, 0-冻结离职',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数字化建档时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '元数据刷新时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_username` (`username`),
KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户员工主表';

-- ------------------------------------------------------------
-- 5. 物理文件实体信息表（去重指纹池，未改动）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `file_info`;
CREATE TABLE `file_info` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '物理文件唯一自增ID',
`file_identifier` varchar(64) NOT NULL COMMENT '文件的 SHA-256 全局唯一哈希指纹（用于秒传去重高空拦截）',
`file_path` varchar(500) NOT NULL COMMENT '在玩客云外置盘/对象存储桶中的绝对物理存储路径',
`file_size` bigint(20) NOT NULL COMMENT '物理文件大小(字节 Byte)',
`file_suffix` varchar(20) DEFAULT NULL COMMENT '文件物理后缀名',
`ref_count` int(11) NOT NULL DEFAULT 1 COMMENT '硬引用计数 (当降为0时触发物理粉碎磁盘实体)',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次入库时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '引用更新时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_identifier` (`file_identifier`) COMMENT '⚡ 极速探空秒传唯一指纹索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物理文件实体信息表';

-- ------------------------------------------------------------
-- 6. 外部链接提取分享表（未改动）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `file_share`;
CREATE TABLE `file_share` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键ID',
`user_id` bigint(20) NOT NULL COMMENT '创建分享的用户 ID',
`user_file_id` bigint(20) NOT NULL COMMENT '分享的网盘逻辑虚拟文件/文件夹 ID(关联 user_file.id)',
`short_link` varchar(20) NOT NULL COMMENT '8位唯一不重复的短链特征码',
`extraction_code` varchar(10) DEFAULT NULL COMMENT '4位随机数字提取口令(null表示免密)',
`expire_time` datetime DEFAULT NULL COMMENT '绝对失效时间戳(null表示永久有效)',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分享链创建时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_short_link` (`short_link`),
KEY `idx_user_file` (`user_id`, `user_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部链接提取分享表';

-- ------------------------------------------------------------
-- 7. 逻辑虚拟文件树表（升级版，挂载到存储库，修复类型）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_file`;
CREATE TABLE `user_file` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '虚拟逻辑自增主键ID',
`user_id` bigint(20) NOT NULL COMMENT '物理操作/文件创建所属人ID',
`repo_id` bigint(20) NOT NULL COMMENT '归属的存储库空间ID (关联 storage_repository.id)',
`parent_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '父文件夹ID，0代表当前存储库的专属逻辑根目录',
`file_name` varchar(255) NOT NULL COMMENT '用户自定义的可视展现文件名',
`is_dir` tinyint NOT NULL DEFAULT 0 COMMENT '是否为文件夹: 0-否, 1-是',
`file_info_id` bigint(20) DEFAULT NULL COMMENT '关联的物理文件ID (外键逻辑关联 file_info.id；若为文件夹则为 NULL)',
`dept_id` bigint(20) DEFAULT NULL COMMENT '历史部门锚定：锁死文件创建时科室ID，防调岗权限漂移',
`deleted` bigint(20) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0-活跃可见；非0(存入当前文件ID)-已入回收站',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '虚拟节点创建/上传时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '虚拟节点最近修改元数据时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_repo_parent_name_del` (`repo_id`, `parent_id`, `file_name`, `deleted`),
KEY `idx_user_id` (`user_id`),
KEY `idx_file_info_id` (`file_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='逻辑虚拟文件树表';

-- ------------------------------------------------------------
-- 8. 分片上传状态机会话表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `upload_session`;
CREATE TABLE `upload_session` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键ID',
`upload_id` varchar(64) NOT NULL COMMENT '大文件哈希标识符',
`status` varchar(32) NOT NULL DEFAULT 'UPLOADING' COMMENT '状态机指针: UPLOADING, MERGING, DONE',
`user_id` bigint(20) NOT NULL COMMENT '上传者ID',
`repo_id` bigint(20) NOT NULL COMMENT '存储库空间ID',
`dept_id` bigint(20) NOT NULL COMMENT '科室部门ID',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话构筑时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '会话更新时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_upload_id` (`upload_id`),
KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式并发分片上传状态机会话表';

SET FOREIGN_KEY_CHECKS = 1;