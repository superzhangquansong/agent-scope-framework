package com.agent.scope.framework.constant;

import java.util.List;

/**
 * 业务常量接口。
 * <p>
 * 集中管理所有业务常量，避免魔法值散落在代码各处。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface BusinessConst {
    String CTX_KEY_SESSION_CONTEXT = "sessionContextCxt";
    /**
     * Bearer Token 前缀
     */
    String BEARER_PREFIX = "Bearer ";

    /**
     * SSE Emitter 超时时间 5 分钟
     */
    long SSE_EMITTER_TIMEOUT = 300_000L;

    // ==================== 工具消息常量 ====================

    /** 查询设备信息成功消息 */
    String MSG_QUERY_DEVICE_SUCCESS = "成功查询到设备信息";
    /** 批量控制设备成功消息 */
    String MSG_BATCH_CONTROL_SUCCESS = "成功批量控制设备";
    /** 查询房屋列表成功消息 */
    String MSG_QUERY_HOME_SUCCESS = "成功查询到房屋列表";
    /** 查询产品信息成功消息 */
    String MSG_QUERY_PRODUCT_SUCCESS = "成功查询到产品信息";

    // ==================== HTTP 状态码常量 ====================

    /** HTTP 成功状态码 */
    int HTTP_OK = 200;
    /** HTTP 请求参数错误状态码 */
    int HTTP_BAD_REQUEST = 400;
    /** HTTP 服务器错误状态码 */
    int HTTP_INTERNAL_ERROR = 500;

    // ==================== 响应体键名常量 ====================

    /** 响应体键名：状态码 */
    String RESPONSE_KEY_CODE = "code";
    /** 响应体键名：数据 */
    String RESPONSE_KEY_DATA = "data";
    /** 响应体键名：消息 */
    String RESPONSE_KEY_MESSAGE = "message";

    // ==================== SSE 事件类型常量 ====================

    /** SSE 事件类型：Agent 开始执行 */
    String SSE_EVENT_AGENT_START = "agent_start";
    /** SSE 事件类型：Agent 执行结束 */
    String SSE_EVENT_AGENT_END = "agent_end";
    /** SSE 事件类型：错误 */
    String SSE_EVENT_ERROR = "error";
    /** SSE 事件类型：权限暂停（HITL 等待用户确认） */
    String SSE_EVENT_PERMISSION_PAUSED = "permission_paused";
    /** SSE 权限暂停提示消息 */
    String MSG_PERMISSION_PAUSED = "Agent 等待权限确认，请回复\"继续\"确认或\"取消\"拒绝";
    /** SSE 残留 ASKING 状态提示消息 */
    String MSG_ASKING_RESIDUAL = "检测到未完成的权限确认，请回复\"继续\"确认或\"取消\"拒绝";
    /** SSE 残留 ASKING 状态无数据提示消息 */
    String MSG_ASKING_RESIDUAL_NO_DATA = "检测到上一轮会话残留的权限确认状态，请开启新的会话";
    /** SSE Agent 恢复执行异常消息 */
    String MSG_AGENT_RESUME_ERROR = "Agent恢复执行异常";
    /** SSE Agent 执行异常消息 */
    String MSG_AGENT_EXECUTION_ERROR = "Agent执行异常";

    // ==================== 错误码常量 ====================

    /** 错误码：Agent 执行异常 */
    String ERROR_CODE_AGENT_ERROR = "AGENT_ERROR";
    /** 错误码：内部错误 */
    String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    // ==================== HITL 权限确认常量 ====================

    /** Redis Key 前缀：待确认权限请求（HITL），完整 Key 格式：pending_confirm:{sessionId} */
    String PENDING_CONFIRM_KEY_PREFIX = "pending_confirm:";
    /** 待确认权限请求的 Redis TTL（分钟），超时自动清除避免会话泄漏 */
    long PENDING_CONFIRM_TTL_MINUTES = 30L;
    /** AgentScope ASKING 状态错误消息关键字（用于检测残留 HITL 状态） */
    String ASKING_ERROR_KEYWORD = "paused for human-in-the-loop confirmation";

    // ==================== 限流常量 ====================

    /** 限流规则名称：模型调用维度 */
    String RATE_LIMIT_RULE_MODEL_CALL = "model-call";
    /** 限流规则名称：HDL API 维度 */
    String RATE_LIMIT_RULE_HDL_API = "hdl-api";
    /** 限流规则名称：用户会话维度 */
    String RATE_LIMIT_RULE_USER_SESSION = "user-session";
    /** 限流提示消息 */
    String MSG_RATE_LIMIT_EXCEEDED = "请求过于频繁，请稍后重试";
    /** 错误码：限流 */
    String ERROR_CODE_RATE_LIMITED = "RATE_LIMITED";

    // ==================== Nacos 配置版本管理常量 ====================

    /** Nacos 配置历史 API 路径（Nacos OpenAPI 固定路径） */
    String NACOS_HISTORY_API_PATH = "/nacos/v1/cs/history";
    /** Nacos 配置 dataId（与 spring.config.import 中一致） */
    String NACOS_CONFIG_DATA_ID = "application-config.yml";

    // ==================== 定时调度模块常量（特性38）====================

    /** 定时唤醒：默认系统提示词（未指定时使用） */
    String SCHEDULER_DEFAULT_SYS_PROMPT = "你是一个由定时调度唤醒的 Agent，请根据触发消息执行相应任务。";
    /** 定时唤醒：默认触发消息（未指定时使用） */
    String SCHEDULER_DEFAULT_WAKEUP_MESSAGE = "定时唤醒执行";
    /** 调度任务注册成功消息 */
    String MSG_SCHEDULER_SCHEDULE_SUCCESS = "调度任务注册成功";
    /** 调度任务取消成功消息 */
    String MSG_SCHEDULER_CANCEL_SUCCESS = "调度任务取消成功";
    /** 查询调度任务列表成功消息 */
    String MSG_SCHEDULER_LIST_SUCCESS = "查询调度任务列表成功";

    // ==================== 任务队列模块常量（特性48）====================

    /** 任务提交成功消息 */
    String MSG_TASK_SUBMIT_SUCCESS = "任务提交成功";
    /** 查询任务状态成功消息 */
    String MSG_TASK_STATUS_SUCCESS = "查询任务状态成功";
    /** 获取任务结果成功消息 */
    String MSG_TASK_RESULT_SUCCESS = "获取任务结果成功";
    /** 任务状态：未知（任务不存在时返回） */
    String TASK_STATUS_UNKNOWN = "UNKNOWN";

    // ==================== A2A 协议常量（特性25）====================

    /** A2A 服务端默认监听端口（独立于 Spring Boot 主端口） */
    int A2A_DEFAULT_PORT = 8081;
    /** A2A AgentCard 默认版本号 */
    String A2A_DEFAULT_VERSION = "1.0.0";
    /** A2A 默认传输协议：JSON-RPC over HTTP */
    String A2A_TRANSPORT_JSON_RPC = "JSON-RPC";
    /** A2A 默认输入/输出模式（纯文本） */
    String A2A_DEFAULT_MODE_TEXT = "text";

    // ==================== AG-UI 协议常量（特性36）====================

    /** AG-UI 默认 Agent 标识（对应 HarnessAgent） */
    String AGUI_DEFAULT_AGENT_ID = "harness";
    /** AG-UI 单次 Agent 运行超时时间（分钟） */
    long AGUI_RUN_TIMEOUT_MINUTES = 5L;

    // ==================== Agent as Tool 常量（特性27）====================

    /** 子 Agent 工具名称前缀（避免与业务工具命名冲突） */
    String AGENT_TOOL_NAME_PREFIX = "agent_";
    /** 子 Agent 默认最大迭代次数 */
    int SUBAGENT_DEFAULT_MAX_ITERS = 5;
    /** 视觉子 Agent 系统提示词（图像识别与描述） */
    String SUBAGENT_VISION_SYS_PROMPT = "你是视觉理解子 Agent，专注于图像识别与描述。请根据用户提供的图像，输出简洁准确的中文描述。";
    /** 知识库子 Agent 系统提示词（技术参数查询） */
    String SUBAGENT_KNOWLEDGE_SYS_PROMPT = "你是知识库查询子 Agent，专注于技术参数、规格与说明书查询。请基于已知信息给出准确回答。";

    // ==================== 技能自动沉淀常量（特性33）====================

    /** 技能文件目录名（位于工作区下） */
    String SKILL_DIR_NAME = "skills";
    /** 技能 Markdown 文件扩展名 */
    String SKILL_FILE_SUFFIX = ".md";
    /** 自动沉淀技能名前缀（与人工编写技能区分） */
    String AUTO_PROMOTED_SKILL_PREFIX = "auto_";
    /** 触发技能沉淀的最小工具调用次数（低于此值视为简单任务，不值得沉淀） */
    int SKILL_PROMOTION_MIN_TOOL_CALLS = 2;
    /** 技能 Markdown 内容最大长度（字符），超出截断避免文件膨胀 */
    int SKILL_CONTENT_MAX_LENGTH = 4096;
    /** 技能元数据键：技能名称 */
    String SKILL_META_KEY_NAME = "name";
    /** 技能元数据键：技能描述 */
    String SKILL_META_KEY_DESCRIPTION = "description";
    /** 技能元数据键：触发条件 */
    String SKILL_META_KEY_TRIGGERS = "triggers";
    /** 技能元数据键：执行步骤 */
    String SKILL_META_KEY_STEPS = "steps";

    // ==================== 技能上传管理常量（特性28增强）====================

    /** 技能上传文件大小上限（字节），默认 64KB */
    int SKILL_UPLOAD_MAX_SIZE_BYTES = 64 * 1024;
    /** 技能名称合法字符正则（仅允许字母、数字、下划线、短横线） */
    String SKILL_NAME_REGEX = "^[a-zA-Z0-9_\\-]+$";
    /** YAML frontmatter 分隔符 */
    String SKILL_FRONTMATTER_DELIMITER = "---";
    /** YAML frontmatter 中键值分隔符 */
    String SKILL_FRONTMATTER_KV_SEPARATOR = ":";

    // ==================== 技能压缩包上传常量（特性28增强）====================

    /** 技能压缩包大小上限（字节），默认 10MB */
    int SKILL_PACKAGE_MAX_SIZE_BYTES = 10 * 1024 * 1024;
    /** 技能压缩包内文件数量上限，防止 zip bomb */
    int SKILL_PACKAGE_MAX_FILE_COUNT = 100;
    /** 技能压缩包允许的扩展名 */
    String SKILL_PACKAGE_SUFFIX_ZIP = ".zip";
    /** 技能定义文件名（每个技能目录下的核心文件） */
    String SKILL_DEFINITION_FILENAME = "SKILL.md";
    /**
     * 压缩包解压时允许的文件扩展名白名单。
     * <p>包含：技能文件、配置文件、静态资源、脚本文件等。</p>
     */
    List<String> SKILL_PACKAGE_ALLOWED_SUFFIXES = List.of(
            // 技能与文档
            ".md", ".txt", ".rst",
            // 配置文件
            ".env", ".json", ".yml", ".yaml", ".xml", ".ini", ".conf", ".properties", ".toml",
            // 脚本文件
            ".sh", ".py", ".js", ".ts", ".sql",
            // 静态资源
            ".html", ".css", ".svg", ".png", ".jpg", ".jpeg", ".gif", ".ico",
            // 数据文件
            ".csv"
    );

    // ==================== Channel 通信常量（特性24）====================

    /** Feishu 渠道平台标识 */
    String CHANNEL_PLATFORM_FEISHU = "feishu";
    /** Feishu 渠道默认 Channel ID */
    String FEISHU_CHANNEL_DEFAULT_ID = "feishu-main";
    /** Feishu Webhook 回调路径默认值 */
    String FEISHU_DEFAULT_CALLBACK_PATH = "/feishu/callback";
    /** Feishu 开放平台 API 默认基址 */
    String FEISHU_DEFAULT_API_BASE = "https://open.feishu.cn";

    // ==================== Channel properties Map 键名常量（特性24）====================

    /** Feishu properties Map 键：appId */
    String FEISHU_PROP_KEY_APP_ID = "appId";
    /** Feishu properties Map 键：appSecret */
    String FEISHU_PROP_KEY_APP_SECRET = "appSecret";
    /** Feishu properties Map 键：encryptKey */
    String FEISHU_PROP_KEY_ENCRYPT_KEY = "encryptKey";
    /** Feishu properties Map 键：verificationToken */
    String FEISHU_PROP_KEY_VERIFICATION_TOKEN = "verificationToken";
    /** Feishu properties Map 键：callbackPath */
    String FEISHU_PROP_KEY_CALLBACK_PATH = "callbackPath";
    /** Feishu properties Map 键：apiBase */
    String FEISHU_PROP_KEY_API_BASE = "apiBase";

    // ==================== MCP 协议常量（特性26）====================

    /** Nacos SDK Properties 键：服务器地址 */
    String NACOS_PROP_KEY_SERVER_ADDR = "serverAddr";
    /** Nacos SDK Properties 键：命名空间 */
    String NACOS_PROP_KEY_NAMESPACE = "namespace";
    /** Nacos SDK Properties 键：用户名 */
    String NACOS_PROP_KEY_USERNAME = "username";
    /** Nacos SDK Properties 键：密码 */
    String NACOS_PROP_KEY_PASSWORD = "password";

    // ==================== 任务队列常量（特性48）====================

    /** 任务类型：Agent 推理任务（通过 HarnessAgent.reply() 执行） */
    String TASK_TYPE_AGENT = "agent";
}