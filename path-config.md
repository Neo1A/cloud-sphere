```sql
CREATE DATABASE IF NOT EXISTS `cloudsphere_netdisk`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_general_ci;
USE `cloudsphere_netdisk`;
```

# 租户用户安全凭证表：user [🟡 脚手架已通 · 容量核验暂未编码]
```sql
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '租户用户自增主键',
`username` varchar(64) NOT NULL COMMENT '唯一登录账户名',
`password` varchar(128) NOT NULL COMMENT '加盐加密密文密码',
`salt` varchar(64) DEFAULT NULL COMMENT '非对称加盐混淆因子',
`status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '账号状态(1-正常活跃, 0-冻结禁封)',
`total_space` bigint(20) NOT NULL DEFAULT '16106127360' COMMENT '网盘分配容量上限(字节, 默认15GB) [🚧 暂未实现拦截]',
`used_space` bigint(20) NOT NULL DEFAULT '0' COMMENT '当前已占用的物理体积(字节) [🚧 暂未实现拦截]',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册打卡时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '资料变更时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户用户安全认证主表';

DROP TABLE IF EXISTS `file_info`;
CREATE TABLE `file_info` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '物理实体资产自增主键',
`file_identifier` varchar(64) NOT NULL COMMENT '文件全局唯一 SHA-256 哈希印记(秒传核心)',
`file_path` varchar(512) NOT NULL COMMENT '本地磁盘/外置存储绝对物理存储路径',
`file_size` bigint(20) NOT NULL COMMENT '文件真实物理体积大小(字节 Byte)',
`file_suffix` varchar(20) DEFAULT NULL COMMENT '文件真实物理后缀名(如 .img, .mp4)',
`ref_count` int(11) NOT NULL DEFAULT '1' COMMENT '多租户共享引用计数器(归零时实施物理删除)',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次物理载荷落盘时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_file_identifier` (`file_identifier`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物理资产仓实体表';

DROP TABLE IF EXISTS `user_file`;
CREATE TABLE `user_file` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '逻辑虚拟树节点自增主键',
`user_id` bigint(20) NOT NULL COMMENT '所属租户用户 ID',
`parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父级虚拟目录主键 ID (顶层根目录恒为 0)',
`file_info_id` bigint(20) DEFAULT NULL COMMENT '关联的物理实体资产 ID (若当前节点为文件夹则留空)',
`file_name` varchar(256) NOT NULL COMMENT '租户逻辑展现文件名/文件夹名',
`is_dir` tinyint(1) NOT NULL DEFAULT '0' COMMENT '虚拟节点属性(1-文件夹, 0-普通文件)',
`deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '回收站软删除标记(1-已删除, 0-长驻可见)',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '虚拟节点创建时间',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '树形节点最近变更时间',
PRIMARY KEY (`id`),
KEY `idx_user_parent` (`user_id`,`parent_id`) USING BTREE,
KEY `idx_file_info` (`file_info_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='逻辑用户虚拟文件树表';

DROP TABLE IF EXISTS `file_chunk`;
CREATE TABLE `file_chunk` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分片事务自增主键',
`identifier` varchar(64) NOT NULL COMMENT '所属大文件的全局唯一 SHA-256 哈希印记',
`chunk_number` int(11) NOT NULL COMMENT '当前分片的位置编号(自 1 开始)',
`chunk_size` bigint(20) NOT NULL COMMENT '当前碎片载荷的真实体积(字节)',
`chunk_path` varchar(512) NOT NULL COMMENT '碎片在本地临时温区的绝对路径',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '碎片落盘存储时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_identifier_chunk` (`identifier`,`chunk_number`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大文件分片临时存储中转表';

DROP TABLE IF EXISTS `file_share`;
CREATE TABLE `file_share` (
`id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分享条目自增主键',
`share_code` varchar(32) NOT NULL COMMENT '提取链接短路由加密令牌 (如 A6B9)',
`user_id` bigint(20) NOT NULL COMMENT '发起分享的源头租户 ID',
`user_file_id` bigint(20) NOT NULL COMMENT '被分享的逻辑树节点 ID',
`share_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '分享类型(0-凭提取码私密分享, 1-公开无密分享)',
`password` varchar(10) DEFAULT NULL COMMENT '4位提取码密匙明文',
`valid_days` int(11) NOT NULL DEFAULT '7' COMMENT '有效时限天数(0代表永久有效)',
`expire_time` datetime NOT NULL COMMENT '绝对失效临界时间点',
`click_count` int(11) NOT NULL DEFAULT '0' COMMENT '分享链接转存/点击热度计数器',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分享创建时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_share_code` (`share_code`) USING BTREE,
KEY `idx_user_share` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='安全时效性私密分享控制表';

SET FOREIGN_KEY_CHECKS = 1;

```