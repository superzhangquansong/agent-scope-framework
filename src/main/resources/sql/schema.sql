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
     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
     `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
    `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
    `house_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '家庭ID（业务隔离用，可为空）',
    `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色：user/assistant/thinking/tool',
    `content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
    `message_timestamp` bigint DEFAULT NULL COMMENT '消息产生的时间戳（毫秒）',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除：0未删1已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息记录表';

-- -------------------------------------------------------------------
-- 2. 模型调用记录表（特性4：MySQL全链路记录 — 模型调用入参出参与Token消费）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_call_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`     VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `reply_id`       VARCHAR(64)           DEFAULT NULL COMMENT '回复ID（关联ModelCallStart/End事件）',
    `output_content` MEDIUMTEXT            DEFAULT NULL COMMENT '模型调用输出内容（累积TextBlock/ThinkingBlock/ToolCall片段）',
    `input_tokens`   BIGINT                DEFAULT 0    COMMENT '输入Token数（prompt tokens）',
    `output_tokens`  BIGINT                DEFAULT 0    COMMENT '输出Token数（completion tokens）',
    `total_tokens`   BIGINT                DEFAULT 0    COMMENT '总Token数（input + output）',
    `cached_tokens`  BIGINT                DEFAULT 0    COMMENT '缓存命中Token数',
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
    `input_tokens`   BIGINT                DEFAULT 0    COMMENT '输入Token数',
    `output_tokens`  BIGINT                DEFAULT 0    COMMENT '输出Token数',
    `total_tokens`   BIGINT                DEFAULT 0    COMMENT '总Token数',
    `model_name`     VARCHAR(64)           DEFAULT NULL COMMENT '模型名称',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `deleted`        TINYINT               DEFAULT 0    COMMENT '逻辑删除：0未删1已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token消耗汇总表';

-- -------------------------------------------------------------------
-- 5. 审计日志表（P0-4：敏感操作审计）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`        VARCHAR(64)           DEFAULT NULL COMMENT '操作用户ID',
    `session_id`     VARCHAR(64)           DEFAULT NULL COMMENT '会话ID',
    `action`         VARCHAR(128) NOT NULL COMMENT '操作类型：CONFIG_ROLLBACK/SESSION_DESTROY/TOOL_EXECUTE/INTERRUPT/PERMISSION_CONFIRM',
    `target`         VARCHAR(256)          DEFAULT NULL COMMENT '操作目标（如配置版本号、工具名等）',
    `detail`         TEXT                  DEFAULT NULL COMMENT '操作详情（JSON）',
    `ip_address`     VARCHAR(64)           DEFAULT NULL COMMENT '请求IP地址',
    `trace_id`       VARCHAR(64)           DEFAULT NULL COMMENT '链路追踪ID',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id`   (`user_id`),
    KEY `idx_action`    (`action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- -------------------------------------------------------------------
-- 6. 任务队列表（P1-9：持久化任务队列，替代内存队列）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_queue_record` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`        VARCHAR(64)  NOT NULL COMMENT '任务唯一ID',
    `session_id`     VARCHAR(64)           DEFAULT NULL COMMENT '关联会话ID',
    `user_id`        VARCHAR(64)           DEFAULT NULL COMMENT '用户ID',
    `task_type`      VARCHAR(64)  NOT NULL COMMENT '任务类型',
    `payload`        MEDIUMTEXT            DEFAULT NULL COMMENT '任务载荷（JSON）',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/DEAD',
    `retry_count`    INT                   DEFAULT 0    COMMENT '重试次数',
    `max_retry`      INT                   DEFAULT 3    COMMENT '最大重试次数',
    `error_message`  TEXT                  DEFAULT NULL COMMENT '错误信息',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_status`     (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务队列记录表';

-- -------------------------------------------------------------------
-- 7. 场景模板表（场景推荐引擎：预置通用场景模板，按用户设备组合匹配推荐）
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scene_template` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`  VARCHAR(64)  NOT NULL COMMENT '模板编码（如 movie_mode）',
    `scene_name`     VARCHAR(128) NOT NULL COMMENT '场景名称（如 观影模式）',
    `description`    VARCHAR(512) NOT NULL COMMENT '场景效果描述（展示给用户看的效果说明）',
    `icon`           VARCHAR(64)           DEFAULT NULL COMMENT '前端图标标识',
    `required_spks`  VARCHAR(512) NOT NULL COMMENT '所需设备种类码JSON数组，如 ["light.rgbcw","hvac.ac"]，用户设备必须全覆盖才能匹配',
    `optional_spks`  VARCHAR(512)          DEFAULT NULL COMMENT '可选设备种类码JSON数组，部分匹配也纳入推荐',
    `device_actions` TEXT         NOT NULL COMMENT '设备动作JSON，key=spk，value=[{key,value}]属性列表',
    `time_slots`     VARCHAR(128)          DEFAULT NULL COMMENT '适用时段（如 evening/night/morning），用于时段加权匹配',
    `priority`       INT                   DEFAULT 50   COMMENT '优先级基础分（越小越高，范围1-100）',
    `enabled`        TINYINT               DEFAULT 1    COMMENT '是否启用：0禁用1启用',
    `create_time`    DATETIME     NOT NULL COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`        TINYINT               DEFAULT 0    COMMENT '逻辑删除：0未删1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场景模板表';

-- 预置场景模板数据（冷启动即用，后续可通过 Nacos 热更新覆盖）
INSERT INTO `scene_template` (`template_code`, `scene_name`, `description`, `icon`, `required_spks`, `optional_spks`, `device_actions`, `time_slots`, `priority`, `enabled`, `create_time`, `update_time`) VALUES
('movie_mode', '观影模式', '灯光调暗至20%暖色温，窗帘关闭，空调开启26度制冷，营造沉浸式观影氛围', 'movie', '["light.rgbcw"]', '["curtain.roller","hvac.ac","light.cct"]', '{"light.rgbcw":[{"key":"on_off","value":"on"},{"key":"brightness","value":20},{"key":"cct","value":3000}],"curtain.roller":[{"key":"on_off","value":"off"}],"hvac.ac":[{"key":"on_off","value":"on"},{"key":"set_temp","value":26},{"key":"mode","value":"cool"}]}', 'evening', 20, 1, NOW(), NOW()),
('sleep_mode', '睡眠模式', '关闭所有灯光，空调开启26度睡眠模式，窗帘关闭，营造安静舒适的睡眠环境', 'sleep', '["light.rgbcw","hvac.ac"]', '["curtain.roller","light.switch","light.dimming"]', '{"light.rgbcw":[{"key":"on_off","value":"off"}],"light.switch":[{"key":"on_off","value":"off"}],"light.dimming":[{"key":"on_off","value":"off"}],"hvac.ac":[{"key":"on_off","value":"on"},{"key":"set_temp","value":26},{"key":"mode","value":"sleep"}],"curtain.roller":[{"key":"on_off","value":"off"}]}', 'night', 15, 1, NOW(), NOW()),
('guest_mode', '会客模式', '灯光调至80%白光，窗帘打开，空调开启制冷，营造明亮舒适的会客氛围', 'guest', '["light.rgbcw"]', '["curtain.roller","hvac.ac","light.cct"]', '{"light.rgbcw":[{"key":"on_off","value":"on"},{"key":"brightness","value":80},{"key":"cct","value":5000}],"curtain.roller":[{"key":"on_off","value":"on"}],"hvac.ac":[{"key":"on_off","value":"on"},{"key":"set_temp","value":24},{"key":"mode","value":"cool"}]}', 'daytime', 40, 1, NOW(), NOW()),
('reading_mode', '阅读模式', '灯光调至100%冷白光，提供明亮护眼的阅读照明', 'reading', '["light.cct"]', '["light.rgbcw","light.dimming"]', '{"light.cct":[{"key":"on_off","value":"on"},{"key":"brightness","value":100},{"key":"cct","value":5500}],"light.rgbcw":[{"key":"on_off","value":"on"},{"key":"brightness","value":100},{"key":"cct","value":5500}],"light.dimming":[{"key":"on_off","value":"on"},{"key":"brightness","value":100}]}', 'evening', 35, 1, NOW(), NOW()),
('wake_up_mode', '起床模式', '窗帘打开50%，灯光渐亮，空调关闭，模拟自然唤醒', 'sunrise', '["curtain.roller"]', '["light.dimming","light.rgbcw","hvac.ac"]', '{"curtain.roller":[{"key":"on_off","value":"on"},{"key":"position","value":50}],"light.dimming":[{"key":"on_off","value":"on"},{"key":"brightness","value":60}],"light.rgbcw":[{"key":"on_off","value":"on"},{"key":"brightness","value":60},{"key":"cct","value":4000}],"hvac.ac":[{"key":"on_off","value":"off"}]}', 'morning', 50, 1, NOW(), NOW()),
('leave_home', '离家模式', '关闭所有灯光和空调，窗帘关闭，确保家中设备安全关闭', 'leave', '["light.switch"]', '["hvac.ac","curtain.roller","light.rgbcw","light.dimming"]', '{"light.switch":[{"key":"on_off","value":"off"}],"light.rgbcw":[{"key":"on_off","value":"off"}],"light.dimming":[{"key":"on_off","value":"off"}],"hvac.ac":[{"key":"on_off","value":"off"}],"curtain.roller":[{"key":"on_off","value":"off"}]}', 'daytime', 30, 1, NOW(), NOW());
