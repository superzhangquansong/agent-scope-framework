-- ===================================================================
-- AgentScope Java 2.0 GA 企业级多智能体框架 —— 数据库建表脚本
-- ===================================================================
-- 数据库：agent_scope_framework
-- 字符集：utf8mb4（防止中文乱码）
-- 说明：包含对话全链路记录所需的所有表结构
-- ===================================================================

-- 创建数据库（若不存在），统一使用 utf8mb4 字符集，避免中文乱码
CREATE DATABASE IF NOT EXISTS `agent_scope_framework`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `agent_scope_framework`;

-- -------------------------------------------------------------------
-- 1. 对话消息记录表（特性4：MySQL全链路记录）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_message_record` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`        VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `user_id`           VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `house_id`          VARCHAR(64)           DEFAULT NULL COMMENT '家庭ID（业务隔离用，可为空）',
    `role`              VARCHAR(32)  NOT NULL COMMENT '消息角色：user/assistant/thinking/tool',
    `content`           MEDIUMTEXT            DEFAULT NULL COMMENT '消息内容',
    `message_timestamp` BIGINT                DEFAULT NULL COMMENT '消息产生的时间戳（毫秒）',
    `create_time`       DATETIME     NOT NULL COMMENT '创建时间',
    `deleted`           TINYINT               DEFAULT 0    COMMENT '逻辑删除：0未删1已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_user_id`    (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息记录表';

-- -------------------------------------------------------------------
-- 2. 模型调用记录表（特性4：MySQL全链路记录 — 模型调用入参出参与Token消费）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_call_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`     VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `reply_id`       VARCHAR(64)           DEFAULT NULL COMMENT '回复ID（关联ModelCallStart/End事件）',
    `output_content` MEDIUMTEXT            DEFAULT NULL COMMENT '模型调用输出内容（累积TextBlock/ThinkingBlock/ToolCall片段）',
    `input_tokens`   INT                   DEFAULT 0    COMMENT '输入Token数（prompt tokens）',
    `output_tokens`  INT                   DEFAULT 0    COMMENT '输出Token数（completion tokens）',
    `total_tokens`   INT                   DEFAULT 0    COMMENT '总Token数（input + output）',
    `cached_tokens`  INT                   DEFAULT 0    COMMENT '缓存命中Token数',
    `model_name`     VARCHAR(64)           DEFAULT NULL COMMENT '模型名称',
    `duration_ms`    BIGINT                DEFAULT 0    COMMENT '调用耗时（毫秒）',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `deleted`        TINYINT               DEFAULT 0    COMMENT '逻辑删除：0未删1已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_reply_id`   (`reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用记录表';

-- -------------------------------------------------------------------
-- 3. 工具调用记录表（特性4：MySQL全链路记录 — 工具调用入参出参与执行状态）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tool_call_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`     VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `tool_call_id`   VARCHAR(64)           DEFAULT NULL COMMENT '工具调用ID',
    `tool_name`      VARCHAR(128) NOT NULL COMMENT '工具名称',
    `arguments`      TEXT                  DEFAULT NULL COMMENT '工具入参（JSON）',
    `result`         MEDIUMTEXT            DEFAULT NULL COMMENT '工具出参',
    `state`          VARCHAR(32)           DEFAULT NULL COMMENT '执行状态：success/error',
    `duration_ms`    BIGINT                DEFAULT 0    COMMENT '执行耗时（毫秒）',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `deleted`        TINYINT               DEFAULT 0    COMMENT '逻辑删除：0未删1已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id`   (`session_id`),
    KEY `idx_tool_call_id` (`tool_call_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用记录表';

-- -------------------------------------------------------------------
-- 4. Token消耗汇总表（特性4：MySQL全链路记录 — Token消费汇总）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `token_usage_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`     VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `input_tokens`   INT                   DEFAULT 0    COMMENT '输入Token数',
    `output_tokens`  INT                   DEFAULT 0    COMMENT '输出Token数',
    `total_tokens`   INT                   DEFAULT 0    COMMENT '总Token数',
    `model_name`     VARCHAR(64)           DEFAULT NULL COMMENT '模型名称',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `deleted`        TINYINT               DEFAULT 0    COMMENT '逻辑删除：0未删1已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token消耗汇总表';
