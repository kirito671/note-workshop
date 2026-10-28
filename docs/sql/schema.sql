-- Note-Workshop 初始数据库脚本
-- 适用环境：MySQL 8.x
-- 注意：本脚本会重建业务表，只用于本地开发、演示和答辩环境，不要直接对生产库执行。

CREATE DATABASE IF NOT EXISTS `note_workshop`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `note_workshop`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `ai_message`;
DROP TABLE IF EXISTS `ai_conversation`;
DROP TABLE IF EXISTS `ai_audit_log`;
DROP TABLE IF EXISTS `note_ai_result`;
DROP TABLE IF EXISTS `note_comment`;
DROP TABLE IF EXISTS `user_follow`;
DROP TABLE IF EXISTS `note_like`;
DROP TABLE IF EXISTS `note_image`;
DROP TABLE IF EXISTS `note`;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(50) NOT NULL COMMENT '登录名',
    `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    `nickname` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar_url` VARCHAR(500) NULL COMMENT '头像访问 URL',
    `role` TINYINT NOT NULL DEFAULT 0 COMMENT '0=普通用户，1=管理员',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常，0=禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE `note` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '作者用户 ID',
    `title` VARCHAR(120) NOT NULL COMMENT '笔记标题',
    `book_name` VARCHAR(200) NOT NULL COMMENT '图书名称',
    `content` TEXT NOT NULL COMMENT '笔记正文',
    `visibility` TINYINT NOT NULL DEFAULT 0 COMMENT '0=私有，1=公开',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常，0=管理员隐藏',
    `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=待审核，1=审核通过，2=审核驳回',
    `audit_remark` VARCHAR(500) NULL COMMENT '审核意见',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_note_user` (`user_id`),
    KEY `idx_note_visibility_status` (`visibility`, `status`, `audit_status`),
    KEY `idx_note_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='读书笔记表';

CREATE TABLE `note_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `note_id` BIGINT NOT NULL COMMENT '所属笔记 ID',
    `image_url` VARCHAR(500) NOT NULL COMMENT '图片访问 URL',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_note_image_note` (`note_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='笔记图片关联表';

CREATE TABLE `note_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '点赞用户 ID',
    `note_id` BIGINT NOT NULL COMMENT '被点赞笔记 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_like_user_note` (`user_id`, `note_id`),
    KEY `idx_note_like_note` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞表';

CREATE TABLE `user_follow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `follower_id` BIGINT NOT NULL COMMENT '关注者用户 ID',
    `following_id` BIGINT NOT NULL COMMENT '被关注用户 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_follow_pair` (`follower_id`, `following_id`),
    KEY `idx_user_follow_following` (`following_id`),
    KEY `idx_user_follow_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系表';

CREATE TABLE `note_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `note_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `parent_id` BIGINT NULL COMMENT '父评论 ID，空表示一级评论',
    `content` VARCHAR(1000) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常，0=逻辑删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_note_comment_note_status` (`note_id`, `status`, `created_at`),
    KEY `idx_note_comment_parent` (`parent_id`),
    KEY `idx_note_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='笔记评论表';

CREATE TABLE `note_ai_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `note_id` BIGINT NOT NULL,
    `summary` TEXT NOT NULL,
    `audience` VARCHAR(500) NOT NULL,
    `completeness_score` TINYINT NOT NULL,
    `relevance_score` TINYINT NOT NULL,
    `depth_score` TINYINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_ai_result_note` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='笔记 AI 辅助结果表';

CREATE TABLE `ai_audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `scene` VARCHAR(40) NOT NULL COMMENT 'NOTE_ANALYZE / CUSTOMER_SERVICE',
    `success` TINYINT NOT NULL COMMENT '1=成功，0=失败',
    `duration_ms` BIGINT NOT NULL DEFAULT 0,
    `error_message` VARCHAR(500) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_audit_user` (`user_id`),
    KEY `idx_ai_audit_scene_created` (`scene`, `created_at`),
    KEY `idx_ai_audit_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用审计日志表';

CREATE TABLE `ai_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(100) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_conversation_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 客服会话表';

CREATE TABLE `ai_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL COMMENT 'USER / ASSISTANT',
    `content` TEXT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_message_conversation_created` (`conversation_id`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 客服消息表';

-- 演示账号 student / 123456、admin / 123456
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `role`, `status`) VALUES
(1, 'admin', '$2a$10$Re0HPy0ebwQ8lO8oYv5u6.uKVQyA/GTlA1RuVfWEAW8XXvwRl223.', '管理员', 1, 1),
(2, 'student', '$2a$10$Re0HPy0ebwQ8lO8oYv5u6.uKVQyA/GTlA1RuVfWEAW8XXvwRl223.', '林间读者', 0, 1);

INSERT INTO `note`
(`id`, `user_id`, `title`, `book_name`, `content`, `visibility`, `status`, `audit_status`, `audit_remark`) VALUES
(1, 2, 'Java 集合：从接口到选择', 'Java 核心技术',
 'ArrayList 适合频繁读取，LinkedList 更适合频繁插入和删除。真正选择时，要先看访问模式，再看数据规模和内存成本。\n\n今天的思考：不要只背结论，要能把底层数组与链表结构讲清楚。',
 1, 1, 1, NULL),
(2, 2, 'MySQL 索引不是越多越好', 'MySQL 必知必会',
 '索引能加快查询，但会增加写入成本和存储占用。设计联合索引时要优先考虑高频查询条件与最左前缀原则。',
 1, 1, 1, NULL),
(3, 2, '一条等待审核的读书笔记', '代码整洁之道',
 '这是用于演示审核流程的公开笔记。管理员通过后，它才会进入公开书架。',
 1, 1, 0, NULL);

INSERT INTO `note_image` (`note_id`, `image_url`, `sort_order`) VALUES
(1, '/images/demo-java-collections.svg', 0),
(2, '/images/demo-mysql-index.svg', 0);

INSERT INTO `note_like` (`user_id`, `note_id`) VALUES
(1, 1);

INSERT INTO `user_follow` (`follower_id`, `following_id`) VALUES
(1, 2);

INSERT INTO `note_comment` (`note_id`, `user_id`, `parent_id`, `content`, `status`) VALUES
(1, 1, NULL, '这个对比很清楚，适合复习集合框架。', 1);