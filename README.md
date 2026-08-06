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
└──────────────────────────┘    │  - SSE 事件转发（14 种 BO）       │
                                │  - 异步全链路落库                 │
                                └──────────┬───────────────────────┘
                                           │
                                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    HarnessAgent（无状态单例）                      │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  ReActAgent（推理核心）                                      │ │
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

### 核心基础特性（1-23）

| 序号 | 特性名称 | 配置类 | 代码位置 | 说明 | 使用方式 |
|:---|:---|:---|:---|:---|:---|
| 1 | 智能体（Agent） | [ReactAgentConfig](src/main/java/com/agent/scope/framework/config/ReactAgentConfig.java) | `ReactAgent.Builder` | ReActAgent 无状态推理核心，提供推理→工具调用→响应循环 | 自动装配，通过 `scope.agentscope.*` 配置 |
| 2 | HarnessAgent 入口 | [HarnessAgentConfig](src/main/java/com/agent/scope/framework/config/HarnessAgentConfig.java) | `HarnessAgent.Builder` | 工程化入口，打包 Workspace/记忆/持久化/子Agent/沙箱 | 自动装配，整合所有特性 |
| 3 | 多用户/多会话并发 | HarnessAgentConfig | `RuntimeContext.builder().userId().sessionId()` | Agent 实例无状态，单实例安全服务多个 (userId, sessionId) | 每次请求构建独立 RuntimeContext |
| 4 | RuntimeContext | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `RuntimeContext.builder()` | 轻量级上下文，携带 SessionContext 用于参数传递 | `streamEvents()` 时自动构建 |
| 5 | 中断执行（Interrupt） | [InterruptConfig](src/main/java/com/agent/scope/framework/config/InterruptConfig.java) | `Agent.interrupt()` | 运行时中断 + 基于状态存储的断点恢复 | `POST /api/chat/interrupt?userId=&sessionId=` |
| 6 | 状态持久化 | [StateStoreConfig](src/main/java/com/agent/scope/framework/config/StateStoreConfig.java) | `RedisAgentStateStore.builder()` | Redis 分布式状态存储，按 (userId, sessionId) 分区 | `scope.agentscope.state-store.type=redis` |
| 7 | ContentBlock 消息模型 | AgentScope 核心 | `TextBlock/ImageBlock/AudioBlock/VideoBlock` | 文本/图片/音频/工具结果/思考统一收敛到 ContentBlock | 自动处理，支持多模态输入 |
| 8 | 事件流系统 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `streamEvents()` | 28 种类型化 AgentEvent，可观测/可交互/可中断 | SSE 自动推送 |
| 9 | 流式输出 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `SseEmitter` | SSE 逐字/逐句推送 | `POST /api/chat/stream` |
| 10 | 结构化输出 | [ToolResultVO](src/main/java/com/agent/scope/framework/vo/ToolResultVO.java) | `ToolResultVO.builder()` | 统一返回格式：success/message/data/routePath/broadcastText | 所有 @Tool 方法返回 ToolResultVO |
| 11 | 多模态 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | `buildUserMessage()` | 支持文本+图片+音频+视频输入 | ChatStreamDTO 的 images/audios/videos 字段 |
| 12 | 中间件（Middleware） | [MiddlewareChainConfig](src/main/java/com/agent/scope/framework/config/MiddlewareChainConfig.java) | `MiddlewareBase` | 五阶段洋葱+管道混合模型（onAgent/onReasoning/onActing/onModelCall/onSystemPrompt） | `scope.agentscope.middleware.enabled=true` |
| 13 | Hook 系统 | MiddlewareChainConfig | `MiddlewareBase` 五个生命周期阶段 | 在五个生命周期阶段插入自定义逻辑 | 实现 MiddlewareBase 接口 |
| 14 | 权限系统 | [PermissionConfig](src/main/java/com/agent/scope/framework/config/PermissionConfig.java) | `PermissionContextState` | 三态决策：允许/需用户批准/拒绝 | `scope.agentscope.permission.enabled=true` |
| 15 | 人机交互（HITL） | PermissionConfig | `permission.ask-tools` | 敏感工具调用强制人工确认 | 配置 `permission.ask-tools: [batch_control_device]` |
| 16 | 模型容错 | [ReactAgentConfig](src/main/java/com/agent/scope/framework/config/ReactAgentConfig.java) | `builder.fallbackModel()` | 最大重试 + 备用模型故障转移 | `scope.agentscope.fallback-model-enabled=true` |
| 17 | 上下文压缩 | [CusCompactionConfig](src/main/java/com/agent/scope/framework/config/CusCompactionConfig.java) | `CompactionConfig.builder()` | 结构化压缩保留任务目标/当前状态/关键发现/后续步骤 | `scope.agentscope.memory.*` 配置阈值 |
| 18 | 工作区 | [WorkspaceConfig](src/main/java/com/agent/scope/framework/config/WorkspaceConfig.java) | `Path agentWorkspacePath` | 独立临时工作目录，支持跨 Agent 共享 | `scope.agentscope.workspace.path=/tmp/agentscope-workspace` |
| 19 | 分布式记忆 | [MemoryToolsConfig](src/main/java/com/agent/scope/framework/config/MemoryToolsConfig.java) | `MemorySearchTool/MemorySaveTool` | 用户级长期记忆，MEMORY.md + 流水账 | `scope.agentscope.advanced.memory-tools-enabled=true` |
| 20 | 文件系统 | [FilesystemConfig](src/main/java/com/agent/scope/framework/config/FilesystemConfig.java) | `LocalFilesystemSpec` | 读写本地文件或 MinIO 对象存储 | `scope.agentscope.advanced.filesystem-type=local/minio` |
| 21 | 沙箱 | [SandboxConfig](src/main/java/com/agent/scope/framework/config/SandboxConfig.java) | `DockerFilesystemSpec` | 安全执行用户上传脚本，支持快照与恢复 | `scope.agentscope.advanced.sandbox-enabled=true` |
| 22 | 子 Agent | [SubagentConfig](src/main/java/com/agent/scope/framework/config/SubagentConfig.java) | `SubagentDeclaration` | 父 Agent 委派任务给子 Agent 并行执行 | `scope.agentscope.subagents` 列表配置 |
| 23 | 计划模式 | [PlanModeConfig](src/main/java/com/agent/scope/framework/config/PlanModeConfig.java) | `PlanModeMiddleware` | 复杂任务先规划再拆分执行，PlanNotebook 管理 | `scope.agentscope.advanced.plan-mode-enabled=true` |

### 分布式与协作特性（24-31）

| 序号 | 特性名称 | 配置类 | 说明 | 使用方式 |
|:---|:---|:---|:---|:---|
| 24 | Channel 通信 | [ChannelGatewayConfig](src/main/java/com/agent/scope/framework/config/ChannelGatewayConfig.java) | Agent 间消息队列/事件总线异步通信 | `scope.agentscope.advanced.channel-enabled=false`（需扩展包） |
| 25 | A2A 协议 | [A2aConfig](src/main/java/com/agent/scope/framework/config/A2aConfig.java) | Nacos 服务发现的 Agent 互调用 | `scope.agentscope.advanced.a2a-enabled=false`（需扩展包） |
| 26 | MCP 协议 | [McpConfig](src/main/java/com/agent/scope/framework/config/McpConfig.java) | 标准化调用外部工具和数据源 | `scope.agentscope.advanced.mcp-enabled=false`（需扩展包） |
| 27 | Agent as Tool | [AgentAsToolConfig](src/main/java/com/agent/scope/framework/config/AgentAsToolConfig.java) | 将 Agent 封装为工具供其他 Agent 调用 | `scope.agentscope.advanced.agent-as-tool-enabled=false` |
| 28 | 技能系统 | [SkillRepositoryConfig](src/main/java/com/agent/scope/framework/config/SkillRepositoryConfig.java) | 本地 ZIP 或远程 Git 仓库动态加载 | `scope.agentscope.advanced.skill-repository-enabled=true` |
| 29 | 内置工具 | [BuiltinToolsConfig](src/main/java/com/agent/scope/framework/config/BuiltinToolsConfig.java) | 时间计算、JSON 解析等通用工具 | `scope.agentscope.builtin-tools.enabled=true` |
| 30 | 会话生命周期 | [SessionLifecycleConfig](src/main/java/com/agent/scope/framework/config/SessionLifecycleConfig.java) | 会话创建/销毁/超时/跨节点迁移 | `scope.agentscope.session.enabled=true` |
| 31 | 多租户隔离 | SessionContext + RuntimeContext | session/user/agent/org 多维度隔离 | 请求携带 userId + sessionId |

### 生产级增强特性（32-48）

| 序号 | 特性名称 | 配置类 | 说明 | 使用方式 |
|:---|:---|:---|:---|:---|
| 32 | 沙箱快照与恢复 | SandboxConfig | 沙箱状态快照，进程重启后恢复长任务 | `scope.agentscope.advanced.sandbox-snapshot-type=local/oss` |
| 33 | 技能自动沉淀 | SkillRepositoryConfig | 成功模式自动生成 Markdown Skill | `scope.agentscope.advanced.skill-repository-enabled=true` |
| 34 | 分布式后端 | [StateStoreConfig](src/main/java/com/agent/scope/framework/config/StateStoreConfig.java) | Redis/MySQL/PostgreSQL/OSS 后端 | `scope.agentscope.state-store.type=redis` |
| 35 | PlanNotebook | [PlanModeConfig](src/main/java/com/agent/scope/framework/config/PlanModeConfig.java) | 结构化任务分解与追踪 | `scope.agentscope.advanced.plan-mode-enabled=true` |
| 36 | AG-UI 协议 | [AgUiConfig](src/main/java/com/agent/scope/framework/config/AgUiConfig.java) | 前端 UI 与 Agent 事件流标准化对接 | `scope.agentscope.advanced.ag-ui-enabled=false` |
| 37 | 异步工具执行 | AgentScope 核心 | Reactor Flux 响应式执行长耗时工具 | 自动支持 |
| 38 | 定时唤醒调度 | [SchedulerConfig](src/main/java/com/agent/scope/framework/config/SchedulerConfig.java) | 周期任务调度，Agent 定时唤醒 | `scope.agentscope.advanced.scheduler-enabled=false`（需 Quartz） |
| 39 | OpenTelemetry | [MiddlewareChainConfig](src/main/java/com/agent/scope/framework/config/MiddlewareChainConfig.java) | 原生集成分布式链路追踪 | `scope.agentscope.advanced.otel-tracing-enabled=true` |
| 40 | Studio 可视化 | [StudioConfig](src/main/java/com/agent/scope/framework/config/StudioConfig.java) | 可视化调试与实时监控 | `scope.agentscope.advanced.studio-enabled=false` |
| 41 | 工具超时控制 | [ToolEnhancementConfig](src/main/java/com/agent/scope/framework/config/ToolEnhancementConfig.java) | 每个 @Tool 独立超时 | `scope.agentscope.tool.timeout-ms=30000` |
| 42 | 工具结果缓存 | [ToolEnhancementConfig](src/main/java/com/agent/scope/framework/config/ToolEnhancementConfig.java) | Redis 缓存高频查询结果 | `scope.agentscope.tool.cache-ttl-seconds=300` |
| 43 | 限流与熔断 | [ResilienceMiddleware](src/main/java/com/agent/scope/framework/middleware/ResilienceMiddleware.java) + [RedisRateLimiterService](src/main/java/com/agent/scope/framework/service/RedisRateLimiterService.java) | Resilience4j + Redis Lua 双层限流 | `resilience4j.*` + `scope.agentscope.redis-rate-limit.*` |
| 44 | 提示词模板 | [PromptTemplateConfig](src/main/java/com/agent/scope/framework/config/PromptTemplateConfig.java) | Nacos 动态加载系统提示词 | `scope.agentscope.prompt-templates.enabled=true` |
| 45 | 可观测性增强 | [ObservabilityConfig](src/main/java/com/agent/scope/framework/config/ObservabilityConfig.java) | Prometheus 指标（调用次数/延迟/Token） | `scope.agentscope.observability.enabled=true` |
| 46 | 配置版本管理 | [ConfigVersionConfig](src/main/java/com/agent/scope/framework/config/ConfigVersionConfig.java) | Nacos 配置回滚和版本对比 | `scope.agentscope.config-version.enabled=true` |
| 47 | Agent 健康检查 | [HealthCheckConfig](src/main/java/com/agent/scope/framework/config/HealthCheckConfig.java) | `/actuator/health` 含 Redis/MySQL 探针 | `scope.agentscope.health-check.enabled=true` |
| 48 | 任务队列 | [TaskQueueConfig](src/main/java/com/agent/scope/framework/config/TaskQueueConfig.java) | RabbitMQ/Kafka 异步执行长耗时任务 | `scope.agentscope.advanced.task-queue-enabled=false`（需 MQ） |

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
# 编译
mvn clean package -DskipTests

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

# 中断 Agent 执行
curl -X POST "http://localhost:8788/api/chat/interrupt?userId=test-user-001&sessionId=test-session-001"
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

---

## API 接口

### 对话接口

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 流式对话 | POST | `/api/chat/stream` | SSE 流式推送 Agent 执行过程 |
| 中断执行 | POST | `/api/chat/interrupt` | 按 userId + sessionId 中断 Agent |

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
| `agent_end` | Agent 执行完成 | sessionId |
| `error` | 错误 | code, message |

---

## 数据库设计

### 数据库：`agent_scope_framework`（UTF8MB4）

| 表名 | 说明 | 关键字段 |
|:---|:---|:---|
| `chat_message_record` | 对话消息记录 | session_id, user_id, role(user/thinking/assistant), content |
| `model_call_record` | 模型调用记录 | session_id, reply_id, output_content, input_tokens, output_tokens, duration_ms |
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
| **Redis** | RedisRateLimiterService | Redis + Lua 滑动窗口分布式限流 |
| **Redis** | HealthCheckConfig | Redis 健康检查探针 |
| **Qdrant** | QdrantProperties | 向量数据库，RAG 知识库语义检索（配置就绪） |
| **MinIO** | FilesystemConfig | 对象存储，Agent 文件系统（配置就绪） |

---

## 部署指南

### 本地开发

```bash
# 1. 启动 MySQL、Redis、Nacos
# 2. 初始化数据库
mysql -u root -p < src/main/resources/sql/schema.sql
# 3. 推送配置到 Nacos（见快速开始）
# 4. 编译运行
mvn clean package -DskipTests
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
├── dto/                # 数据传输对象（ChatStreamDTO）
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
