# AgentScope Java 2.0 GA 企业级多智能体应用框架

> 基于 [AgentScope Java 2.0 GA](https://java.agentscope.io/v2/zh/docs/index.html) 官方框架构建的企业级分布式多智能体应用系统，实现高可用、高并发、易维护、易扩展的生产级部署。

---

## 目录

- [项目简介](#项目简介)
- [技术栈](#技术栈)
- [架构设计](#架构设计)
- [核心特性清单（48 项）](#核心特性清单48-项)
- [快速开始](#快速开始)
- [配置指南](#配置指南)
- [API 接口](#api-接口)
- [数据库设计](#数据库设计)
- [中间件使用](#中间件使用)
- [部署指南](#部署指南)
- [官方文档参考](#官方文档参考)
- [项目结构](#项目结构)
- [License](#license)

---

## 项目简介

本项目基于 AgentScope Java 2.0 GA 官方框架，采用**双层 Agent 架构**：

- **ReActAgent**：无状态推理核心，提供"推理 → 工具调用 → 响应"的 ReAct 循环。Agent 实例完全无状态，所有可变状态通过 Reactor Context 传播，单实例可安全服务多个 `(userId, sessionId)` 组合。
- **HarnessAgent**：通过 Middleware 和 Toolkit 通道扩展 ReActAgent，增加 Workspace、记忆、沙箱、子 Agent、技能和计划模式等工程基础设施。

### 核心能力

| 能力 | 说明 |
|:---|:---|
| 智能路由 | LLM 自主理解用户意图，选择并调用注册的业务工具 |
| 全链路追溯 | 用户提问 → Agent 思考 → 工具调用 → 模型调用 → Token 消费 → 最终回复 |
| 多租户隔离 | userId + sessionId 五层隔离（请求/上下文/运行时/状态/数据） |
| 实时思考链 | SSE 流式推送 14 种事件类型，前端实时渲染完整推理过程 |
| 分布式限流 | Redis + Lua 滑动窗口限流，多实例共享计数器 |
| 配置热更新 | Nacos 配置中心，@RefreshScope Bean 30 秒内自动重建 |

### 特性落地说明

本 README 如实标注每项特性的落地状态：

- ✅ **已落地**：代码真实装配并生效，可在生产环境使用
- ⚠️ **部分落地**：核心功能可用，但存在孤儿 Bean、未注册工具或子能力缺失
- ❌ **未落地**：仅创建配置类占位，未实际装配到 Agent，或缺少依赖包

---

## 技术栈

| 类别 | 技术 | 版本 |
|:---|:---|:---|
| 语言 | Java | 22 |
| 框架 | Spring Boot | 4.0.7 |
| 微服务 | Spring Cloud + Spring Cloud Alibaba | 2025.1.0 |
| AI 框架 | AgentScope Java | 2.0.0 GA |
| ORM | MyBatis-Plus | 3.5.16 |
| 数据库 | MySQL | 9.1.0 |
| 缓存 | Redis（Lettuce） | - |
| 向量库 | Qdrant | 1.12.0 |
| 对象存储 | MinIO | 8.5.12 |
| 配置中心 | Nacos | - |
| 限流熔断 | Resilience4j + Redis Lua | 2.2.0 |
| 可观测性 | OpenTelemetry + Prometheus + Zipkin | - |
| 大模型 | DashScope（通义千问）+ Ollama | - |

---

## 架构设计

```
┌──────────────────────────────────────────────────────────────────┐
│                        前端（SSE Client）                         │
└──────────────┬───────────────────────────────────┬───────────────┘
               │ ChatStreamDTO                     │ SSE Events
               ▼                                   ▲
┌──────────────────────────┐    ┌──────────────────────────────────┐
│    ChatController         │    │  ChatService                     │
│  POST /api/chat/stream    │───▶│  - 构建 RuntimeContext            │
│  POST /api/chat/interrupt │    │  - 委托 HarnessAgent.streamEvents│
│  POST /api/chat/confirm   │    │  - SSE 事件转发（14 种 BO）       │
└──────────────────────────┘    │  - 异步全链路落库                 │
                                └──────────┬───────────────────────┘
                                           │
                                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    HarnessAgent（无状态单例）                      │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  ReActAgent（推理核心，delegate）                            │ │
│  │  Reasoning → Acting → Observation 循环                      │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐ │
│  │ Middleware  │ │  Toolkit   │ │ Compaction │ │  StateStore  │ │
│  │ (五阶段)    │ │ (业务工具) │ │ (上下文压缩)│ │  (Redis)     │ │
│  └────────────┘ └────────────┘ └────────────┘ └──────────────┘ │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐ │
│  │ Workspace  │ │  Memory    │ │ Permission │ │  SubAgent    │ │
│  │ (工作区)    │ │ (长期记忆) │ │ (权限HITL) │ │  (子Agent)   │ │
│  └────────────┘ └────────────┘ └────────────┘ └──────────────┘ │
└──────────────────────────────────────────────────────────────────┘
           │                    │                    │
           ▼                    ▼                    ▼
    ┌──────────┐        ┌──────────┐        ┌──────────┐
    │  MySQL   │        │  Redis   │        │ DashScope│
    │ (全链路) │        │ (状态/缓存)│       │ (通义千问)│
    └──────────┘        └──────────┘        └──────────┘
```

---

## 核心特性清单（48 项）

> 每项特性均标注落地状态（✅已落地 / ⚠️部分落地 / ❌未落地）与实际实现效果。

### 核心基础特性（1-23）

| 序号 | 特性名称 | 配置类 | 代码位置 | 实现效果 | 落地 | 使用方式 |
|:---|:---|:---|:---|:---|:---|:---|
| 1 | 智能体（Agent） | [ReactAgentConfig](src/main/java/com/agent/scope/framework/config/ReactAgentConfig.java) | `ReActAgent.Builder` | ReActAgent 无状态推理核心，提供"推理→工具调用→响应"循环；LLM 自主理解用户自然语言意图，调用注册工具完成任务 | ✅ | 自动装配，`scope.agentscope.*` 配置 |
| 2 | HarnessAgent 入口 | [HarnessAgentConfig](src/main/java/com/agent/scope/framework/config/HarnessAgentConfig.java) | `HarnessAgent.Builder.fromAgent()` | 工程化入口，整合 Workspace/记忆/持久化/子Agent/技能仓库；@Primary 标记为系统主入口 Agent | ✅ | 自动装配 |
| 3 | 多用户/多会话并发 | HarnessAgentConfig | `RuntimeContext.builder().userId().sessionId()` | Agent 实例无状态，单实例安全服务多个 (userId, sessionId) 组合；同一 (userId, sessionId) FIFO 串行，不同 session 完全并行 | ✅ | 每次请求构建独立 RuntimeContext |
| 4 | RuntimeContext | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `RuntimeContext.builder()` | 轻量级 per-call 上下文，携带 SessionContext（accessToken/houseId 等）通过类型化属性层注入工具方法 | ✅ | `streamEvents()` 时自动构建 |
| 5 | 中断执行（Interrupt） | [InterruptController](src/main/java/com/agent/scope/framework/controller/InterruptController.java) | `harnessAgent.getDelegate().interrupt(userId, sessionId)` | 调用中断接口后，ReAct 循环在下一检查点（reasoning/acting/streaming chunk）终止，AgentState 自动保存到 Redis，返回 `GenerateReason.INTERRUPTED` 标记；下次同 session 调用从中断点恢复 | ✅ | `POST /api/chat/interrupt?userId=&sessionId=` |
| 6 | 状态持久化 | [StateStoreConfig](src/main/java/com/agent/scope/framework/config/StateStoreConfig.java) | `RedisAgentStateStore.builder()` | Redis 分布式状态存储，按 (userId, sessionId) 分区；每次 call 后自动保存对话上下文/权限规则/工具状态，跨节点会话恢复 | ✅ | `scope.agentscope.state-store.type=redis` |
| 7 | ContentBlock 消息模型 | AgentScope 核心 | `TextBlock/ImageBlock/AudioBlock/VideoBlock` | 文本/图片/音频/视频/工具结果/思考统一收敛到 ContentBlock；多模态输入自动构造对应 Block | ✅ | 自动处理 |
| 8 | 事件流系统 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `streamEvents()` | 28 种类型化 AgentEvent，使用 instanceof 按事件类型分别处理，仅序列化必要字段（如 TextBlockDeltaEvent 只转发 delta） | ✅ | SSE 自动推送 |
| 9 | 流式输出 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `SseEmitter` | SSE 逐字/逐句推送模型输出与工具执行进度；doOnComplete 统一关闭连接，避免提前关闭导致事件丢失 | ✅ | `POST /api/chat/stream` |
| 10 | 结构化输出 | [ToolResultVO](src/main/java/com/agent/scope/framework/vo/ToolResultVO.java) | `ToolResultVO.builder()` | 统一返回格式：success/message/data/routePath/broadcastText/askUser/needConfirm；所有 @Tool 方法返回此 VO，LLM 可读 askUser 字段决定后续行为 | ✅ | 所有 @Tool 方法返回 ToolResultVO |
| 11 | 多模态 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `buildUserMessage()` | 支持文本+图片+音频+视频输入；图片自动转 Base64Source/URLSource 构造 ImageBlock | ✅ | ChatStreamDTO 的 images/audios/videos 字段 |
| 12 | 中间件（Middleware） | [MiddlewareChainConfig](src/main/java/com/agent/scope/framework/config/MiddlewareChainConfig.java) | `MiddlewareBase` | 五阶段洋葱+管道混合模型（onAgent/onReasoning/onActing/onModelCall/onSystemPrompt）；OtelTracing/Observability/Resilience/ToolEnhancement 四个自定义中间件接入链 | ✅ | `scope.agentscope.middleware.enabled=true` |
| 13 | Hook 系统 | MiddlewareChainConfig | `MiddlewareBase` 五个生命周期阶段 | 在五个生命周期阶段插入自定义逻辑；order() 方法控制执行顺序（数值越大越外层） | ✅ | 实现 MiddlewareBase 接口 |
| 14 | 权限系统 | [PermissionConfig](src/main/java/com/agent/scope/framework/config/PermissionConfig.java) | `PermissionContextState` | 三态决策（ALLOW/DENY/ASK）；采用 ACCEPT_EDITS 模式，工作目录文件放行，敏感路径（.ssh/.env 等）强制 ASK | ✅ | `scope.agentscope.permission.enabled=true` |
| 15 | 人机交互（HITL） | [PermissionConfig](src/main/java/com/agent/scope/framework/config/PermissionConfig.java) | `permission.ask-tools` | 配置了需要人机交互（HITL）的 tool 在调用时会去咨询用户是否确认：Agent 暂停发出 RequireUserConfirmEvent，前端回复"继续"则继续执行工具调用，回复"取消"则拒绝工具调用并产生 LLM 可见错误结果；待确认请求持久化到 Redis（TTL 30 分钟）防服务重启丢失 | ✅ | 配置 `permission.ask-tools: [batch_control_device]`，`POST /api/chat/confirm` 恢复 |
| 16 | 模型容错 | [ReactAgentConfig](src/main/java/com/agent/scope/framework/config/ReactAgentConfig.java) | `builder.maxRetries().fallbackModel()` | 主模型不可用时自动切换备用模型（如 qwen-plus 故障切 qwen-turbo）；配置最大重试次数，重试耗尽后触发 fallback | ✅ | `scope.agentscope.fallback-model-enabled=true` |
| 17 | 上下文压缩 | [CusCompactionConfig](src/main/java/com/agent/scope/framework/config/CusCompactionConfig.java) | `CompactionConfig.builder()` | 触发阈值（消息数/Token 数）后结构化压缩，保留任务目标/当前状态/关键发现/后续步骤；keepMessages 保留最近 N 条不压缩 | ✅ | `scope.agentscope.memory.*` 配置阈值 |
| 18 | 工作区 | [WorkspaceConfig](src/main/java/com/agent/scope/framework/config/WorkspaceConfig.java) | `Path agentWorkspacePath` | 独立临时工作目录，支持跨 Agent 共享；AGENTS.md/MEMORY.md/tools.json 等资产按目录组织 | ✅ | `scope.agentscope.workspace.path=/tmp/agentscope-workspace` |
| 19 | 分布式记忆 | [MemoryToolsConfig](src/main/java/com/agent/scope/framework/config/MemoryToolsConfig.java) | `MemorySearchTool/MemorySaveTool` | 创建了 4 个记忆工具（MemorySearch/MemoryGet/MemorySave/SessionSearch），但作为 `List<Object>` Bean 返回，ToolkitConfig 扫描 ArrayList.class 无 @Tool 注解，工具未注册到 Toolkit；MEMORY.md 静态资产随工作区装配存在，但 Agent 无法主动调用记忆工具 | ⚠️ | `scope.agentscope.advanced.memory-tools-enabled=true`（工具注册需修复） |
| 20 | 文件系统 | [FilesystemConfig](src/main/java/com/agent/scope/framework/config/FilesystemConfig.java) | `LocalFilesystemSpec` | 仅创建 LocalFilesystemSpec Bean，HarnessAgentConfig 明确注释"暂不直接装配到 Builder（类型不匹配）"，仅 log.info("已就绪（暂未装配）")；Agent 无 read_file/write_file/edit_file/grep/glob/ls 工具能力 | ❌ | `scope.agentscope.advanced.filesystem-type=local/minio`（配置就绪，未装配） |
| 21 | 沙箱 | [SandboxConfig](src/main/java/com/agent/scope/framework/config/SandboxConfig.java) | `DockerFilesystemSpec` | 仅创建快照规范 Bean（LocalSnapshotSpec），未创建任何沙箱执行环境（无 DockerFilesystemSpec 实例），无 execute(shell) 工具；快照 Bean 也是孤儿从未被注入使用 | ❌ | `scope.agentscope.advanced.sandbox-enabled=true`（缺扩展包依赖） |
| 22 | 子 Agent | [SubagentConfig](src/main/java/com/agent/scope/framework/config/SubagentConfig.java) | `SubagentDeclaration` | 父 Agent 委派任务给子 Agent 并行执行；nacos 配置了 vision/knowledge 两个子 Agent 声明，通过 builder.subagents() 装配；支持同步（timeout>0）与后台（timeout=0）两种模式 | ✅ | `scope.agentscope.subagents` 列表配置 |
| 23 | 计划模式 | [PlanModeConfig](src/main/java/com/agent/scope/framework/config/PlanModeConfig.java) | `PlanModeMiddleware` | 创建了 PlanModeMiddleware Bean，但 HarnessAgentConfig 明确注释"实现的是 HarnessRuntimeMiddleware 而非 MiddlewareBase，无法并入 middlewares 列表，故不在此处装配"；中间件 Bean 从未接入 Agent，计划模式不生效，无 plan_enter/plan_write/plan_exit 工具 | ❌ | `scope.agentscope.advanced.plan-mode-enabled=true`（配置就绪，未装配） |

### 分布式与协作特性（24-31）

| 序号 | 特性名称 | 配置类 | 实现效果 | 落地 | 使用方式 |
|:---|:---|:---|:---|:---|:---|
| 24 | Channel 通信 | [ChannelGatewayConfig](src/main/java/com/agent/scope/framework/config/ChannelGatewayConfig.java) | `channelGatewayConfigs()` 返回空 ArrayList，注释承认"实际 Channel 实例需对应扩展包依赖，此处仅装配配置占位"；无任何 FeishuChannel/DingTalkChannel 实例，Agent 间无消息队列/事件总线通信 | ❌ | `scope.agentscope.advanced.channel-enabled=false`（需扩展包） |
| 25 | A2A 协议 | [A2aConfig](src/main/java/com/agent/scope/framework/config/A2aConfig.java) | 类无任何 @Bean 方法，仅有 static {} 块输出日志"占位模式：当前 agentscope 版本未提供 a2a API，暂不装配服务端"；纯空壳类 | ❌ | `scope.agentscope.advanced.a2a-enabled=false`（需扩展包） |
| 26 | MCP 协议 | [McpConfig](src/main/java/com/agent/scope/framework/config/McpConfig.java) | `mcpClients()` 返回空 `List<Object>`，注释"需 agentscope-extensions-mcp 扩展包"；pom.xml 中无该依赖，无 McpClientWrapper 实例，无 mcp__server__tool 工具注册 | ❌ | `scope.agentscope.advanced.mcp-enabled=false`（需扩展包） |
| 27 | Agent as Tool | [AgentAsToolConfig](src/main/java/com/agent/scope/framework/config/AgentAsToolConfig.java) | `agentAsToolInitializer()` 仅返回 String "agent-as-tool-initialized"，无任何 `toolkit.registerTool()` 调用；纯日志占位 | ❌ | `scope.agentscope.advanced.agent-as-tool-enabled=false` |
| 28 | 技能系统 | [SkillRepositoryConfig](src/main/java/com/agent/scope/framework/config/SkillRepositoryConfig.java) | `new FileSystemSkillRepository(skillsDir)` 真实构建并通过 `builder.skillRepository(repo)` 装配；Agent 可通过 `load_skill_through_path` 工具主动加载 SKILL.md 能力包；支持项目全局/工作区/用户级四层来源 | ✅ | `scope.agentscope.advanced.skill-repository-enabled=true` |
| 29 | 内置工具 | [BuiltinToolsConfig](src/main/java/com/agent/scope/framework/config/BuiltinToolsConfig.java) | `new TodoTools()` + `toolkit.registerTool(toolBean)` 真实注册；Agent 可使用 todo_write 工具管理任务清单 | ✅ | `scope.agentscope.builtin-tools.enabled=true` |
| 30 | 会话生命周期 | [SessionLifecycleConfig](src/main/java/com/agent/scope/framework/config/SessionLifecycleConfig.java) | 创建了 SessionLifecycleManager（含 destroySession/listSessions/sessionExists），但该 Bean 从未被任何 Controller/Service 注入使用——孤儿 Bean；实际会话创建/恢复由框架 RedisAgentStateStore 自动处理 | ⚠️ | `scope.agentscope.session.enabled=true`（Manager 未被调用） |
| 31 | 多租户隔离 | SessionContext + RuntimeContext | userId + sessionId 二元组在请求层（ChatStreamDTO）、上下文层（RuntimeContext）、运行时层（Reactor Context）、状态层（RedisAgentStateStore 分区）、数据层（chat_message_record.user_id/session_id）五层隔离 | ✅ | 请求携带 userId + sessionId |

### 生产级增强特性（32-48）

| 序号 | 特性名称 | 配置类 | 实现效果 | 落地 | 使用方式 |
|:---|:---|:---|:---|:---|:---|
| 32 | 沙箱快照与恢复 | [SandboxConfig](src/main/java/com/agent/scope/framework/config/SandboxConfig.java) | 创建 LocalSnapshotSpec Bean，但该 Bean 从未被任何类注入使用——孤儿 Bean；且无沙箱执行环境（见特性21），快照无从产生；无 saveSnapshot/restoreSnapshot 调用代码 | ❌ | `scope.agentscope.advanced.sandbox-snapshot-type=local/oss`（未装配） |
| 33 | 技能自动沉淀 | [SkillRepositoryConfig](src/main/java/com/agent/scope/framework/config/SkillRepositoryConfig.java) | 代码中无任何自动生成 Skill 的逻辑（无 propose_skill/saveSkill 实现），仅注释提及"自动沉淀"；SkillRepositoryConfig 只做只读加载（`new FileSystemSkillRepository`），无 SkillPromoter/SkillCurator 装配 | ❌ | `scope.agentscope.advanced.skill-repository-enabled=true`（仅只读加载） |
| 34 | 分布式后端 | [StateStoreConfig](src/main/java/com/agent/scope/framework/config/StateStoreConfig.java) | RedisAgentStateStore 真实构建并装配（与特性6同一实现）；支持跨节点会话恢复 | ✅ | `scope.agentscope.state-store.type=redis` |
| 35 | PlanNotebook | [PlanModeConfig](src/main/java/com/agent/scope/framework/config/PlanModeConfig.java) | 代码中无 PlanNotebook 类的 import 或引用，仅 javadoc 注释提及；实际使用的是 PlanModeManager（不同类），且 PlanModeMiddleware 本身也未装配到 Agent（见特性23） | ❌ | `scope.agentscope.advanced.plan-mode-enabled=true`（未装配） |
| 36 | AG-UI 协议 | [AgUiConfig](src/main/java/com/agent/scope/framework/config/AgUiConfig.java) | 类无任何 @Bean 方法，仅有 static {} 块输出"占位模式：当前 agentscope 版本未提供 agui API"；纯空壳类 | ❌ | `scope.agentscope.advanced.ag-ui-enabled=false` |
| 37 | 异步工具执行 | AgentScope 核心 | Reactor Flux 响应式执行长耗时工具；`harnessAgent.streamEvents().doOnNext().doOnComplete().subscribe()` 异步驱动，工具在 boundedElastic 线程池执行 | ✅ | 自动支持 |
| 38 | 定时唤醒调度 | [SchedulerConfig](src/main/java/com/agent/scope/framework/config/SchedulerConfig.java) | 尝试 `QuartzAgentScheduler.builder().autoStart(true).build()`，但：①默认 disabled；②pom.xml 无 agentscope-extensions-scheduler 依赖；③无任何 `scheduler.schedule()` 调用——即使装配也无任务可执行 | ❌ | `scope.agentscope.advanced.scheduler-enabled=false`（需 Quartz 扩展包） |
| 39 | OpenTelemetry | [MiddlewareChainConfig](src/main/java/com/agent/scope/framework/config/MiddlewareChainConfig.java) | `new OtelTracingMiddleware()` 加入中间件链；onAgent/onModelCall/onActing 三阶段打点，span: invoke_agent/chat/execute_tool；Zipkin 端点配置就绪 | ✅ | `scope.agentscope.advanced.otel-tracing-enabled=true` |
| 40 | Studio 可视化 | [StudioConfig](src/main/java/com/agent/scope/framework/config/StudioConfig.java) | `studioInitializer()` 仅返回 String "studio-enabled"；无 /studio 端点、无 Controller、无 Studio 集成代码；注释声称"通过 http://host:port/studio 访问"但该路径不存在 | ❌ | `scope.agentscope.advanced.studio-enabled=false` |
| 41 | 工具超时控制 | [ToolEnhancementConfig](src/main/java/com/agent/scope/framework/config/ToolEnhancementConfig.java) | Duration Bean 用于构造 ToolEnhancementMiddleware 接入中间件链；每个 @Tool 独立超时控制 | ✅ | `scope.agentscope.tool.timeout-ms=30000` |
| 42 | 工具结果缓存 | [ToolEnhancementConfig](src/main/java/com/agent/scope/framework/config/ToolEnhancementConfig.java) | `ToolResultCache`（Redis-based，含 get/put/evict）用于构造 ToolEnhancementMiddleware 接入链；高频查询结果缓存到 Redis | ✅ | `scope.agentscope.tool.cache-ttl-seconds=300` |
| 43 | 限流与熔断 | [ResilienceMiddleware](src/main/java/com/agent/scope/framework/middleware/ResilienceMiddleware.java) + [RedisRateLimiterService](src/main/java/com/agent/scope/framework/service/RedisRateLimiterService.java) | Resilience4j 层✅：RateLimiter+CircuitBreaker 装配到中间件链，nacos 配置 modelCallRateLimiter/modelCallCircuitBreaker 实例；Redis Lua 层❌：RedisRateLimiterService 实现了完整 Lua 脚本限流，但该 @Service 从未被任何类注入使用——孤儿服务，分布式限流未生效 | ⚠️ | `resilience4j.*` + `scope.agentscope.redis-rate-limit.*`（Resilience4j 可用，Redis Lua 未接入） |
| 44 | 提示词模板 | [PromptTemplateConfig](src/main/java/com/agent/scope/framework/config/PromptTemplateConfig.java) | `PromptTemplateHolder` Bean 持有从 Nacos 动态加载的系统提示词；HarnessAgentConfig 优先从 holder 读取，回退 DEFAULT_SYSTEM_PROMPT；@RefreshScope 支持 Nacos 热更新 | ✅ | `scope.agentscope.prompt-templates.enabled=true` |
| 45 | 可观测性增强 | [ObservabilityConfig](src/main/java/com/agent/scope/framework/config/ObservabilityConfig.java) | Counter/Timer Bean 注册到 MeterRegistry；ObservabilityMiddleware 接入中间件链；`/actuator/prometheus` 端点暴露指标（调用次数/延迟/Token） | ✅ | `scope.agentscope.observability.enabled=true` |
| 46 | 配置版本管理 | [ConfigVersionConfig](src/main/java/com/agent/scope/framework/config/ConfigVersionConfig.java) | 创建 ConfigVersionManager（内部类，含 ConcurrentHashMap 快照），但该 Bean 从未被任何类注入使用——孤儿 Bean；recordSnapshot() 从未被调用；注释承认"实际版本回滚通过 Nacos 控制台或 OpenAPI 执行" | ⚠️ | `scope.agentscope.config-version.enabled=true`（Manager 未被调用） |
| 47 | Agent 健康检查 | [HealthCheckConfig](src/main/java/com/agent/scope/framework/config/HealthCheckConfig.java) | `redisHealthIndicator` 真实 ping Redis；`mysqlHealthIndicator` 真实 getConnection()；Spring Boot Actuator `/actuator/health` 自动暴露 | ✅ | `scope.agentscope.health-check.enabled=true` |
| 48 | 任务队列 | [TaskQueueConfig](src/main/java/com/agent/scope/framework/config/TaskQueueConfig.java) | 创建 TaskQueueManager 内部类，但：①默认 disabled；②pom.xml 无 spring-boot-starter-amqp / spring-kafka 依赖；③submitTask() 仅生成 UUID + log，无 RabbitTemplate/KafkaTemplate 投递；④getTaskStatus() 硬编码返回 "PENDING"；Manager Bean 从未被使用 | ❌ | `scope.agentscope.advanced.task-queue-enabled=false`（需 MQ 依赖） |

### 落地统计

| 状态 | 数量 | 占比 | 特性序号 |
|:---|:---|:---|:---|
| ✅ 已落地 | 30 | 62% | 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,22,28,29,31,34,37,39,41,42,44,45,47 |
| ⚠️ 部分落地 | 4 | 8% | 19,30,43,46 |
| ❌ 未落地 | 14 | 30% | 20,21,23,24,25,26,27,32,33,35,36,38,40,48 |

**未落地特性的常见模式**：
1. **纯空壳类**（6 项）：A2A/AG-UI/Agent-as-Tool/Studio/Channel/MCP——连实质 @Bean 都没有，仅 static log 或返回 String
2. **Config 创建 Bean 但 HarnessAgentConfig 拒绝装配**（2 项）：文件系统/计划模式——代码注释明确说"暂不装配"
3. **孤儿 Bean**（4 项）：沙箱快照/会话生命周期/配置版本/Redis 限流——Bean 创建了但全代码无人注入使用
4. **README 宣称但代码不存在**（2 项）：技能自动沉淀/PlanNotebook——仅注释提及，无实现代码
5. **pom.xml 缺失依赖**（3 项）：定时调度/任务队列/MCP——缺扩展包依赖

---

## 快速开始

### 环境要求

- Java 22+
- Maven 3.9+
- MySQL 8.0+
- Redis 7.0+
- Nacos 2.x
- Qdrant 1.12+（可选，RAG 知识库）
- MinIO（可选，对象存储）

### 1. 初始化数据库

```bash
# 执行建表脚本（自动创建数据库，UTF8MB4 编码防乱码）
mysql -u root -p < src/main/resources/sql/schema.sql
```

### 2. 推送配置到 Nacos

1. 在 Nacos 创建命名空间 `agent-scope-framework-dev`
2. 在该命名空间下创建配置：
   - **Data ID**: `application-config.yml`
   - **Group**: `SCOPE_GROUP`
   - **内容**: 复制 `src/main/resources/nacos-application-config.yml` 文件内容
3. 修改配置中的数据库、Redis、DashScope API Key 等连接信息

### 3. 编译运行

```bash
# 编译（需 JDK 22）
JAVA_HOME=/path/to/jdk-22 mvn clean package -DskipTests

# 运行
java -jar target/agent-scope-start.jar
```

### 4. 发起对话

```bash
# SSE 流式对话
curl -N -X POST http://localhost:8788/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session-001",
    "userId": "test-user-001",
    "userMessage": "查询我的设备列表"
  }'

# 中断 Agent 执行（per-session，不影响其他并发会话）
curl -X POST "http://localhost:8788/api/chat/interrupt?userId=test-user-001&sessionId=test-session-001"

# HITL 权限确认（敏感工具调用暂停后恢复）
curl -X POST "http://localhost:8788/api/chat/confirm" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session-001",
    "userId": "test-user-001",
    "userMessage": "继续"
  }'
```

---

## 配置指南

### 配置架构

```
application.yaml          ← 仅 Nacos 连接信息（引导配置）
nacos-application-config.yml ← 所有业务配置（推送到 Nacos）
```

### Nacos 配置热更新

所有 `@ConfigurationProperties` + `@RefreshScope` 的 Bean 在 Nacos 配置变更后 30 秒内自动重建：

| 配置类 | 前缀 | 热更新 |
|:---|:---|:---|
| AgentScopeProperties | `scope.agentscope` | ✅ |
| DashScopeProperties | `scope.agentscope.dashscope` | ✅ |
| OllamaProperties | `scope.agentscope.ollama` | ✅ |
| HdlApiProperties | `scope.agentscope.hdl-api` | ✅ |
| QdrantProperties | `scope.agentscope.qdrant` | ✅ |
| MinioProperties | `scope.agentscope.minio` | ✅ |
| EmbeddingProperties | `scope.agentscope.embedding` | ✅ |

### 限流配置

```yaml
scope:
  agentscope:
    redis-rate-limit:
      enabled: true
      key-prefix: "rate_limit:"
      default-limit: 20
      default-window-seconds: 1
      rules:
        - name: model-call       # 模型调用：20 req/s
          limit: 20
          window-seconds: 1
        - name: hdl-api           # HDL API：100 req/s
          limit: 100
          window-seconds: 1
        - name: user-session      # 用户会话：10 req/s
          limit: 10
          window-seconds: 1
```

> **注意**：Redis Lua 限流服务（RedisRateLimiterService）当前为孤儿 Bean，未被任何中间件注入使用。实际生效的是 Resilience4j 的 RateLimiter/CircuitBreaker（见特性43）。

---

## API 接口

### 对话接口

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 流式对话 | POST | `/api/chat/stream` | SSE 流式推送 Agent 执行过程 |
| 中断执行 | POST | `/api/chat/interrupt` | 按 userId + sessionId 中断 Agent（per-session） |
| 权限确认 | POST | `/api/chat/confirm` | HITL 暂停后恢复 Agent 执行 |

### SSE 事件类型

| 事件类型 | 说明 | 关键字段 |
|:---|:---|:---|
| `agent_start` | Agent 开始执行 | sessionId |
| `thinking_start` | 思考块开始 | replyId, blockId |
| `thinking_delta` | 思考增量文本 | delta |
| `thinking_end` | 思考块结束 | blockId |
| `tool_call_start` | 工具调用开始 | toolCallId, toolName |
| `tool_call_delta` | 工具入参增量 | arguments |
| `tool_call_end` | 工具入参构造完成 | toolCallId |
| `tool_result_start` | 工具执行开始 | toolCallId |
| `tool_result_text_delta` | 工具结果增量 | delta |
| `tool_result_end` | 工具执行结束 | toolCallId, state |
| `text_delta` | 最终回复增量 | delta |
| `text_end` | 文本块结束 | blockId |
| `model_call_start` | 模型调用开始 | replyId |
| `model_call_end` | 模型调用结束 | replyId, tokens, duration |
| `permission_ask` | HITL 权限确认请求 | replyId, toolCalls |
| `permission_paused` | Agent 因权限确认暂停 | message |
| `agent_end` | Agent 执行完成 | sessionId |
| `error` | 错误 | code, message |

---

## 数据库设计

### 数据库：`agent_scope_framework`（UTF8MB4，utf8mb4_unicode_ci）

| 表名 | 说明 | 关键字段 |
|:---|:---|:---|
| `chat_message_record` | 对话消息记录 | session_id, user_id, house_id, role(user/thinking/assistant), content, message_timestamp |
| `model_call_record` | 模型调用记录 | session_id, reply_id, output_content, input_tokens, output_tokens, total_tokens, cached_tokens, model_name, duration_ms |
| `tool_call_record` | 工具调用记录 | session_id, tool_call_id, tool_name, arguments, result, state, duration_ms |
| `token_usage_record` | Token 消耗汇总 | session_id, input_tokens, output_tokens, total_tokens, model_name |

### 全链路追溯示例

通过一个 `sessionId` 可追溯完整对话链路：

```sql
-- 1. 查询用户问了什么
SELECT * FROM chat_message_record WHERE session_id = 'xxx' AND role = 'user';

-- 2. 查询 Agent 思考了什么
SELECT * FROM chat_message_record WHERE session_id = 'xxx' AND role = 'thinking';

-- 3. 查询调用了哪些工具
SELECT * FROM tool_call_record WHERE session_id = 'xxx';

-- 4. 查询每次模型调用的 Token 消耗
SELECT * FROM model_call_record WHERE session_id = 'xxx';

-- 5. 查询总 Token 消耗
SELECT * FROM token_usage_record WHERE session_id = 'xxx';

-- 6. 查询最终回复
SELECT * FROM chat_message_record WHERE session_id = 'xxx' AND role = 'assistant';
```

---

## 中间件使用

| 中间件 | 使用位置 | 用途 |
|:---|:---|:---|
| **MySQL** | ChatRecordService | 4 张表全链路记录（对话/模型调用/工具调用/Token） |
| **Redis** | StateStoreConfig | RedisAgentStateStore 分布式状态存储（按 userId+sessionId 分区） |
| **Redis** | ToolEnhancementConfig | ToolResultCache 工具结果缓存（特性42） |
| **Redis** | RedisRateLimiterService | Redis + Lua 滑动窗口分布式限流（⚠️孤儿服务，未接入） |
| **Redis** | HealthCheckConfig | Redis 健康检查探针 |
| **Qdrant** | QdrantProperties | 向量数据库，RAG 知识库语义检索（配置就绪，未实际使用） |
| **MinIO** | FilesystemConfig | 对象存储，Agent 文件系统（❌配置就绪，未装配到 Builder） |

---

## 部署指南

### 本地开发

```bash
# 1. 启动 MySQL、Redis、Nacos
# 2. 初始化数据库
mysql -u root -p < src/main/resources/sql/schema.sql
# 3. 推送配置到 Nacos（见快速开始）
# 4. 编译运行（需 JDK 22）
JAVA_HOME=/path/to/jdk-22 mvn clean package -DskipTests
java -jar target/agent-scope-start.jar
```

### K8s 部署

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agent-scope-framework
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: app
          image: agent-scope-framework:latest
          ports:
            - containerPort: 8788
          env:
            - name: NACOS_SERVER_ADDR
              value: "nacos:8848"
            - name: NACOS_NAMESPACE
              value: "agent-scope-framework-dev"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8788
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8788
```

---

## 官方文档参考

| 文档 | 地址 |
|:---|:---|
| AgentScope Java 2.0 GA 官方文档 | https://java.agentscope.io/v2/zh/docs/index.html |
| 智能体（Agent） | https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html |
| 消息与事件 | https://java.agentscope.io/v2/zh/docs/building-blocks/message-and-event.html |
| 工具（Tool） | https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html |
| 中间件（Middleware） | https://java.agentscope.io/v2/zh/docs/building-blocks/middleware.html |
| 权限系统 | https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html |
| 状态管理（Context） | https://java.agentscope.io/v2/zh/docs/building-blocks/context.html |
| 模型（Model） | https://java.agentscope.io/v2/zh/docs/building-blocks/model.html |
| HarnessAgent 架构 | https://java.agentscope.io/v2/zh/docs/harness/architecture.html |
| 工作区 | https://java.agentscope.io/v2/zh/docs/harness/workspace.html |
| 记忆系统 | https://java.agentscope.io/v2/zh/docs/harness/memory.html |
| 子 Agent | https://java.agentscope.io/v2/zh/docs/harness/subagent.html |
| 技能系统 | https://java.agentscope.io/v2/zh/docs/harness/skill.html |
| 文件系统 | https://java.agentscope.io/v2/zh/docs/harness/filesystem.html |
| 沙箱 | https://java.agentscope.io/v2/zh/docs/harness/sandbox.html |
| Channel 通信 | https://java.agentscope.io/v2/zh/docs/harness/channel.html |
| 计划模式 | https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html |
| AgentScope GitHub | https://github.com/agentscope-ai/agentscope-java |
| DashScope 通义千问 | https://help.aliyun.com/zh/dashscope/ |
| Spring Boot 4.x | https://docs.spring.io/spring-boot/ |
| Spring Cloud Alibaba | https://sca.aliyun.com/ |
| Resilience4j | https://resilience4j.readme.io/ |
| MyBatis-Plus | https://baomidou.com/ |
| Qdrant | https://qdrant.tech/documentation/ |
| MinIO | https://min.io/docs/minio/linux/index.html |

---

## 项目结构

```
src/main/java/com/agent/scope/framework/
├── bo/event/           # SSE 事件 BO（14 种）
├── config/             # 配置类（48 项特性各一个 Config）
│   ├── infrastructure/ # 基础设施配置（Redis）
│   └── properties/     # 配置属性（7 个 Properties 类，全部 @RefreshScope）
├── constant/           # 常量（BusinessConst / FileConst）
├── context/            # 会话上下文（SessionContext）
├── controller/         # 控制器（Chat / Interrupt）
├── dto/                # 数据传输对象（ChatStreamDTO / PermissionConfirmDTO）
├── entity/             # 实体类（4 张表）
├── enums/              # 枚举（AgentEventEnum / ImageTypeEnum / MediaTypeEnum）
├── mapper/             # MyBatis-Plus Mapper（4 个）
├── middleware/         # 中间件（Resilience / Observability / ToolEnhancement）
├── service/            # 服务（ChatService / ChatRecordService / RedisRateLimiterService）
├── tool/               # 业务工具（DeviceTool / HomeTool / ProductTool）
├── utils/              # 工具类
└── vo/                 # 值对象（ToolResultVO）
```

---

## License

MIT License
