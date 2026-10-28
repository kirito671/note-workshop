-- Note-Workshop V2 增量迁移
-- 适用场景：已存在 P0 基础表，只需补充 P1/P2 表结构。
-- 本脚本只执行一次；执行前请先备份数据库。

USE `note_workshop`;

ALTER TABLE `note`
    ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=待审核，1=审核通过，2=审核驳回' AFTER `status`,
    ADD COLUMN `audit_remark` VARCHAR(500) NULL COMMENT '审核意见' AFTER `audit_status`;

UPDATE `note` SET `audit_status` = 1 WHERE `audit_status` IS NULL;

CREATE TABLE IF NOT EXISTS `user_follow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `follower_id` BIGINT NOT NULL COMMENT '关注者用户 ID',
    `following_id` BIGINT NOT NULL COMMENT '被关注用户 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_follow_pair` (`follower_id`, `following_id`),
    KEY `idx_user_follow_following` (`following_id`),
    KEY `idx_user_follow_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系表';

CREATE TABLE IF NOT EXISTS `note_comment` (
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

CREATE TABLE IF NOT EXISTS `note_ai_result` (
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

CREATE TABLE IF NOT EXISTS `ai_audit_log` (
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

CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(100) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_conversation_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 客服会话表';

CREATE TABLE IF NOT EXISTS `ai_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL COMMENT 'USER / ASSISTANT',
    `content` TEXT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_message_conversation_created` (`conversation_id`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 客服消息表';