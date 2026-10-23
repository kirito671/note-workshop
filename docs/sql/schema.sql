-- Note-Workshop Day 3 schema
-- Database: MySQL 8.x
-- Scope: user / note / note_image / note_like
-- Advanced tables (comment, ai_log, follow) are intentionally deferred.

CREATE DATABASE IF NOT EXISTS `note_workshop`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `note_workshop`;

SET FOREIGN_KEY_CHECKS = 0;
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
    `book_name` VARCHAR(200) NOT NULL COMMENT '图书名称字符串，暂不单独建图书表',
    `content` TEXT NOT NULL COMMENT '笔记正文',
    `visibility` TINYINT NOT NULL DEFAULT 0 COMMENT '0=私有，1=公开',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常，0=管理员隐藏',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_note_user` (`user_id`),
    KEY `idx_note_visibility_status` (`visibility`, `status`),
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

-- 两个演示账号的密码都是 123456，数据库中保存的是 BCrypt 哈希。
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `role`, `status`) VALUES
(1, 'admin', '$2a$10$Re0HPy0ebwQ8lO8oYv5u6.uKVQyA/GTlA1RuVfWEAW8XXvwRl223.', '管理员', 1, 1),
(2, 'student', '$2a$10$Re0HPy0ebwQ8lO8oYv5u6.uKVQyA/GTlA1RuVfWEAW8XXvwRl223.', '测试学生', 0, 1);

INSERT INTO `note` (`user_id`, `title`, `book_name`, `content`, `visibility`, `status`) VALUES
(2, 'Java 集合学习笔记', 'Java 核心技术', 'ArrayList 适合频繁查询，LinkedList 更适合频繁插入和删除。', 1, 1),
(2, 'MySQL 索引复习', '高性能 MySQL', '联合索引需要关注最左前缀原则。', 0, 1);

INSERT INTO `note_image` (`note_id`, `image_url`, `sort_order`) VALUES
(1, '/uploads/demo/java-collections.png', 0),
(2, '/uploads/demo/mysql-index.png', 0);

INSERT INTO `note_like` (`user_id`, `note_id`) VALUES
(1, 1);