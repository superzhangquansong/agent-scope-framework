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

> **解读约定**：每项"实现效果"用大白话讲清楚——这玩意儿到底干啥的、在框架里怎么串起来的、用户/开发能感知到什么。

| 序号 | 特性名称 | 配置类 | 落地 | 实现效果（大白话 + 框架描述） | 使用方式 |
|:---|:---|:---|:---|:---|:---|
| 1 | 智能体（Agent） | [ReactAgentConfig](src/main/java/com/agent/scope/framework/config/ReactAgentConfig.java) | ✅ | **一句话**：Agent 是会"想-做-看"循环的智能体内核。**框架里怎么串的**：用 `ReActAgent.Builder` 构建一个无状态单例，Reasoning（让大模型想）→ Acting（调用工具做）→ Observation（看工具结果）循环往复，直到模型觉得"可以回话了"就停下。**用户感知**：发一句"开客厅灯"，Agent 自己琢磨该调哪个工具、怎么调，不用写 if-else 判断用户说啥。 | 自动装配，`scope.agentscope.*` 配置 |
| 2 | HarnessAgent 入口 | [HarnessAgentConfig](src/main/java/com/agent/scope/framework/config/HarnessAgentConfig.java) | ✅ | **一句话**：HarnessAgent 是给裸 Agent 套上工程外壳的入口。**框架里怎么串的**：通过 `HarnessAgent.Builder.fromAgent(reActAgent)` 把 ReActAgent 当作 delegate 包起来，外面叠 Workspace 工作区、Memory 记忆、Toolkit 工具集、Compaction 压缩、StateStore 状态存储、Subagent 子 Agent、SkillRepository 技能仓库、Middleware 中间件链。**用户感知**：服务启动后系统自动有一个 @Primary 标记的 HarnessAgent Bean，所有请求都走它，开发不用关心装配顺序。 | 自动装配 |
| 3 | 多用户/多会话并发 | HarnessAgentConfig | ✅ | **一句话**：一个 Agent 实例就能同时服务所有用户，互不打扰。**框架里怎么串的**：Agent 实例只持有不可变配置（system prompt、模型、工具），所有可变状态都装进 `AgentState` 用 `(userId, sessionId)` 索引。同一 (userId, sessionId) 的请求自动 FIFO 串行避免对话乱序，不同 session 完全并行。**用户感知**：A 用户问"开灯"和 B 用户问"关空调"同时进来，框架保证他俩的状态不串味儿，结果各自正确。 | 每次请求构建独立 RuntimeContext |
| 4 | RuntimeContext | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | ✅ | **一句话**：RuntimeContext 是每次请求的"随身行李箱"，装着这次对话是谁的、在哪个房屋、token 是啥。**框架里怎么串的**：`RuntimeContext.builder().userId().sessionId().put(CTX_KEY_SESSION_CONTEXT, ctx).build()`，把 SessionContext（含 accessToken/houseId）塞进类型化属性层，工具方法执行时能直接拿到。**用户感知**：用户调 `/api/chat/stream` 时传的 userId/houseId 会被原样带到工具里，工具调 HDL 接口时用对的 token。 | `streamEvents()` 时自动构建 |
| 5 | 中断执行（Interrupt） | [InterruptController](src/main/java/com/agent/scope/framework/controller/InterruptController.java) | ✅ | **一句话**：用户点"停止"，Agent 立刻刹车，不会继续烧 Token。**框架里怎么串的**：双机制兜底——① 调 `harnessAgent.getDelegate().interrupt(userId, sessionId)` 设 session 级中断标志，ReAct 循环下个检查点终止；② `ChatService.interruptSession(sessionId)` 直接 dispose Reactor 订阅并关闭 SSE，避免 AgentScope 2.0.0 框架中断信号在检查点未能及时生效时 Agent 继续跑 5+ 轮。中断后 AgentState 自动存 Redis（`GenerateReason.INTERRUPTED`），下次同 session 调用从中断点恢复。**用户感知**：长任务跑一半不想等了，点停止立刻断流，下次还能接着问。 | `POST /api/chat/interrupt?userId=&sessionId=` |
| 6 | 状态持久化 | [StateStoreConfig](src/main/java/com/agent/scope/framework/config/StateStoreConfig.java) | ✅ | **一句话**：Agent 的"记忆"存 Redis，服务重启不丢。**框架里怎么串的**：`RedisAgentStateStore.builder()` 构建分布式状态存储，按 (userId, sessionId) 分区。每次 `call()` 入口从 Redis 加载 AgentState（对话历史、权限规则、工具状态、Plan 状态等），call 退出时自动写回。**用户感知**：滚动发布 / 节点切换 / 宕机重启后，用户接着上次的对话继续聊，没有"我刚才说啥来着"的尴尬。 | `scope.agentscope.state-store.type=redis` |
| 7 | ContentBlock 消息模型 | AgentScope 核心 | ✅ | **一句话**：所有类型的内容（文字、图、语音、视频、工具结果、思考过程）都收敛成统一的"块"。**框架里怎么串的**：`Msg.getContent()` 返回 `List<ContentBlock>`，每个块是 TextBlock / ImageBlock / AudioBlock / VideoBlock / ToolUseBlock / ToolResultBlock / ThinkingBlock 之一。按 role 严格校验，非法组合在构造期就报错。**用户感知**：发图+文字、收工具结果+回复都走同一条数据通道，前端渲染逻辑统一。 | 自动处理 |
| 8 | 事件流系统 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | ✅ | **一句话**：Agent 干的每一步都变成"事件"流出去，前端实时跟上。**框架里怎么串的**：`streamEvents()` 订阅 Reactor Flux，收到 28 种类型化 AgentEvent（TextBlockDeltaEvent / ToolCallStartEvent / ModelCallEndEvent 等），通过策略模式 + 注册表模式（`AgentEventHandlerRegistry.dispatch()`）按事件 Class O(1) 查找对应处理器，仅序列化必要字段（如 TextBlockDeltaEvent 只发 delta 不发全量）。**用户感知**：前端能实时显示"Agent 正在思考…"→"调用 query_device_list 工具"→"返回 3 台设备"的完整链路，不是干等最终结果。 | SSE 自动推送 |
| 9 | 流式输出 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | ✅ | **一句话**：模型吐一个字前端就看到一个字，不用等整段生成完。**框架里怎么串的**：基于 `SseEmitter` 把 Reactor Flux 的事件逐个 `emitter.send()` 给前端；`doOnComplete` 统一关闭连接（不提前关，避免记忆整合后事件丢失），`doOnError` 兜底发 error 事件。**用户感知**：打字机效果，体验丝滑；中断时前端立刻收到 agent_end 事件。 | `POST /api/chat/stream` |
| 10 | 结构化输出 | [ToolResultVO](src/main/java/com/agent/scope/framework/vo/ToolResultVO.java) | ✅ | **一句话**：所有工具返回统一格式，LLM 一眼能读、前端能直接渲染。**框架里怎么串的**：每个 `@Tool` 方法返回 `ToolResultVO.builder().success().message().data().routePath().broadcastText().askUser().needConfirm().build()`。LLM 看 askUser 字段决定要不要追问用户，看 broadcastText 决定要不要广播。**用户感知**：调"开灯"工具返回 `{success:true, broadcastText:"客厅灯已开"}`，LLM 直接拿去回话，不用自己编。 | 所有 @Tool 方法返回 ToolResultVO |
| 11 | 多模态 | [ChatService](src/main/java/com/agent/scope/framework/service/ChatService.java) | ✅ | **一句话**：能发图、发语音、发视频给 Agent，它都看得懂。**框架里怎么串的**：`buildUserMessage()` 把 ChatStreamDTO 里的 images/audios/videos 字段转成 `Base64Source` 或 `URLSource`，构造 ImageBlock / AudioBlock / VideoBlock 拼到 Msg.content 里。**用户感知**：拍张家电铭牌发过去，Agent 调视觉模型识别型号再查设备列表，全程不用手打字。 | ChatStreamDTO 的 images/audios/videos 字段 |
| 12 | 中间件（Middleware） | [MiddlewareChainConfig](src/main/java/com/agent/scope/framework/config/MiddlewareChainConfig.java) | ✅ | **一句话**：在不改 Agent 代码的前提下，给执行流程的五个关键位置插自定义逻辑。**框架里怎么串的**：`MiddlewareBase` 五阶段洋葱+管道混合模型——`onAgent`（包整次 reply）、`onReasoning`（包一轮推理）、`onActing`（包一次工具调用）、`onModelCall`（包底层模型 API）、`onSystemPrompt`（变换 system prompt）。order() 数值越大越外层。装配了 OtelTracing / Observability / Resilience / ToolEnhancement 四个自定义中间件。**用户感知**：开发者想加日志、限流、追踪、改 prompt，写个中间件丢进链里就行，不动业务代码。 | `scope.agentscope.middleware.enabled=true` |
| 13 | Hook 系统 | MiddlewareChainConfig | ✅ | **一句话**：就是特性 12 那 5 个生命周期钩子，每个钩子能干一件事。**框架里怎么串的**：与特性 12 同源——onAgent/onReasoning/onActing/onModelCall/onSystemPrompt 五个位置，每个位置都可以在 `next.apply(input)` 前后插逻辑、观察事件流。**用户感知**：开发者实现 MiddlewareBase 接口，重写需要的位置即可，其余位置默认透传。 | 实现 MiddlewareBase 接口 |
| 14 | 权限系统 | [PermissionConfig](src/main/java/com/agent/scope/framework/config/PermissionConfig.java) | ✅ | **一句话**：每次工具调用都过一遍"能不能执行"的安检。**框架里怎么串的**：`PermissionContextState` 三态决策——ALLOW（直接执行）、DENY（拒绝并返回错误给 LLM）、ASK（暂停问用户）。采用 ACCEPT_EDITS 模式，工作目录内文件操作放行，危险路径（`.ssh`/`.env`/`.aws` 等）强制 ASK。规则分静态预配置和 ASK 时用户接受的建议规则两种来源。**用户感知**：用户让 Agent "删数据库"，Agent 不会真删，而是停下来问"这个操作有风险，确认吗？"。 | `scope.agentscope.permission.enabled=true` |
| 15 | 人机交互（HITL） | [PermissionConfig](src/main/java/com/agent/scope/framework/config/PermissionConfig.java) | ✅ | **一句话**：碰到敏感工具，Agent 暂停问用户"行不行"，用户回"继续"才真执行。**框架里怎么串的**：nacos 配 `permission.ask-tools: [batch_control_device]` 后，该工具被 ASK 决策拦截，Agent 发 `RequireUserConfirmEvent` 暂停，待确认请求（含 ToolCallInfo 完整入参）持久化到 Redis（TTL 30 分钟防服务重启丢失）。前端调 `POST /api/chat/confirm` 携带 `ConfirmResult` 消息（metadata 里塞 `Msg.METADATA_CONFIRM_RESULTS`），Agent 把 ASKING 状态的工具调用替换为 ALLOWED（确认）或写入 DENIED 结果（拒绝），从中断点继续 ReAct 循环。**用户感知**：用户说"关所有灯"，Agent 不直接关，而是先弹"将批量控制 N 台设备，继续？"，用户回"继续"才真关，回"取消"则 Agent 把拒绝结果给 LLM 让它重新规划。 | 配置 `permission.ask-tools: [batch_control_device]`，`POST /api/chat/confirm` 恢复 |
| 16 | 模型容错 | [ReactAgentConfig](src/main/java/com/agent/scope/framework/config/ReactAgentConfig.java) | ✅ | **一句话**：主模型挂了自动切备用，不用人工干预。**框架里怎么串的**：`builder.maxRetries()` 配重试次数，`fallbackModel()` 配备用模型。主模型（如 qwen-plus）调用失败重试耗尽后，自动切到 fallback（如 qwen-turbo）继续推理。**用户感知**：通义千问某个区域抖动，用户无感知，Agent 自动用备用模型继续跑。 | `scope.agentscope.fallback-model-enabled=true` |
| 17 | 上下文压缩 | [CusCompactionConfig](src/main/java/com/agent/scope/framework/config/CusCompactionConfig.java) | ✅ | **一句话**：对话太长时自动摘要前半段，省 Token 又不丢关键信息。**框架里怎么串的**：`CompactionConfig.builder().triggerMessages().keepMessages().build()` 配触发阈值。达到阈值（消息数或 Token 数）时，CompactionMiddleware 调一次 LLM 把前缀压成结构化摘要（SESSIONINTENT / SUMMARY / ARTIFACTS / NEXT STEPS 四段），保留尾部最近 N 条原文。`flushBeforeCompact=true` 压缩前先把新事实抽到 MEMORY.md；`offloadBeforeCompact=true` 原始消息整段写到永不压缩的 `*.log.jsonl`。还有溢出兜底：模型真报 context_length_exceeded 时强制 `triggerMessages=1` 压缩重试一次。**用户感知**：聊 100 轮也不报"上下文超限"，Agent 还能记住前面说的关键事。 | `scope.agentscope.memory.*` 配置阈值 |
| 18 | 工作区 | [WorkspaceConfig](src/main/java/com/agent/scope/framework/config/WorkspaceConfig.java) | ✅ | **一句话**：Agent 的"硬盘"，跨调用跨重启要保留的东西都存这里。**框架里怎么串的**：`Path agentWorkspacePath` 独立临时工作目录，目录布局按官方规范——AGENTS.md（人格）、MEMORY.md（长期记忆）、memory/（日流水账）、knowledge/（领域知识）、subagents/（子 Agent 规格）、skills/（技能）、plans/（计划文件）、tools.json（工具白名单）、agents/<agentId>/sessions/（运行时状态）。每轮 system prompt 自动注入 AGENTS.md。**用户感知**：Agent 跨会话记住用户偏好（"用户喜欢冷色调灯光"），重启后还能查到。 | `scope.agentscope.workspace.path=/tmp/agentscope-workspace` |
| 19 | 分布式记忆 | [MemoryToolsConfig](src/main/java/com/agent/scope/framework/config/MemoryToolsConfig.java) | ✅ | **一句话**：Agent 能主动查记忆、存记忆，跨会话越长越聪明。**框架里怎么串的**：`MemoryToolsInitializer` Bean 注入 `Toolkit`，调 `toolkit.registerTool()` 把 4 个记忆工具（MemorySearchTool / MemoryGetTool / MemorySaveTool / SessionSearchTool）直接注册到 Toolkit——和业务工具统一管理，Agent 可自主调用。`memory_search query="..."` 关键词扫 MEMORY.md + memory/*.md 返回命中；`memory_save` 把关键事实写流水账；后台节流任务（默认 30 分钟一次）跑 MEMORY.md 合并 + 90 天流水账归档。**用户感知**：用户第一次说"我家有 RGB 灯带"，下次会话 Agent 主动记得，不用再说一遍。 | `scope.agentscope.advanced.memory-tools-enabled=true` |
| 20 | 文件系统 | [FilesystemConfig](src/main/java/com/agent/scope/framework/config/FilesystemConfig.java) | ✅ | **一句话**：Agent 能 read_file / write_file / edit_file / grep / glob / ls，文件读写能力完整装配。**框架里怎么串的**：`FilesystemConfig` 创建 `LocalFilesystemSpec` Bean，HarnessAgentConfig 通过 `builder.filesystem(fs)` 装配到 HarnessAgent。支持 local / minio 两种后端类型，由 `scope.agentscope.advanced.filesystem-type` 配置切换。沙箱文件系统（K8s）启用时优先于本地文件系统。**用户感知**：Agent 能读写工作区文件、保存中间结果、检索文件内容，支持代码解释器与文档处理场景。 | `scope.agentscope.advanced.filesystem-type=local/minio` |
| 21 | 沙箱 | [SandboxConfig](src/main/java/com/agent/scope/framework/config/SandboxConfig.java) | ✅ | **一句话**：把 Agent 执行的 shell 命令关进 K3s 隔离容器，避免 Docker daemon 依赖。**框架里怎么串的**：`SandboxConfig` 装配 fabric8 `KubernetesClient` → `SandboxSnapshotSpec`（策略模式按 local/oss/noop 切换）→ `KubernetesFilesystemSpec`（配置 namespace/image/workspaceRoot/containerName/serviceAccount/cpuRequest/memoryRequest），注入到 HarnessAgentConfig 的 `builder.filesystem()` 优先于本地文件系统。pom.xml 已添加 agentscope-extensions-sandbox-kubernetes + fabric8/kubernetes-client 依赖。**用户感知**：Agent 在独立 K8s Pod 内执行不可信 Shell/Python 代码，宿主机安全隔离；进程重启后可从快照恢复长任务状态。 | `scope.agentscope.advanced.sandbox-enabled=true` |
| 22 | 子 Agent | [SubagentConfig](src/main/java/com/agent/scope/framework/config/SubagentConfig.java) | ✅ | **一句话**：主 Agent 把活儿分给子 Agent 并行干，自己等结果汇总。**框架里怎么串的**：nacos 配置 vision / knowledge 两个子 Agent 声明，`SubagentConfig` 解析成 `List<SubagentDeclaration>`，通过 `builder.subagents(declarations)` 装配。运行时主 Agent 调 `agent_spawn` / `agent_send` 工具委派任务，支持同步（timeout>0，主 Agent 等结果）与后台（timeout=0，子 Agent 跑完后通过 system-reminder 反向推送，无需轮询）两种模式。**用户感知**：用户说"分析这张图并查相关文档"，主 Agent spawn 一个 vision 子 Agent 看图、一个 knowledge 子 Agent 查文档，并行跑完汇总回复。 | `scope.agentscope.subagents` 列表配置 |
| 23 | 计划模式 | [PlanModeConfig](src/main/java/com/agent/scope/framework/config/PlanModeConfig.java) | ✅ | **一句话**：让 Agent 先"想清楚写下来"再动手，通过 PlanModeManager 启用计划模式。**框架里怎么串的**：`PlanModeConfig` 创建 `PlanModeManager` Bean，HarnessAgentConfig 通过 `builder.enablePlanMode()` 装配到 HarnessAgent。计划模式下 Agent 先规划再执行，支持 plan_enter / plan_write / plan_exit 三阶段切换，计划文件持久化到 plans/ 目录。**用户感知**：复杂任务时 Agent 先出执行计划（"1. 查设备 2. 开灯 3. 确认"），用户确认后再逐步执行，避免盲目操作。 | `scope.agentscope.advanced.plan-mode-enabled=true` |

### 分布式与协作特性（24-31）

| 序号 | 特性名称 | 配置类 | 落地 | 实现效果（大白话 + 框架描述） | 使用方式 |
|:---|:---|:---|:---|:---|:---|
| 24 | Channel 通信 | [ChannelGatewayConfig](src/main/java/com/agent/scope/framework/config/ChannelGatewayConfig.java) | ✅ | **一句话**：Agent 接入飞书 IM 平台，实现双向消息通信。**框架里怎么串的**：`ChannelGatewayConfig` 装配 `ChannelConfig`（channelId + defaultAgentId 指向 HarnessAgent）、`FeishuChannelRegistry`（单例注册表）、`FeishuChannel`（通过 `FeishuChannel.fromProperties()` 工厂创建，配置 appId/appSecret/encryptKey/verificationToken/callbackPath/apiBase）。入站：飞书 Webhook → FeishuCallbackController → FeishuChannel → ChannelRouter → HarnessAgent；出站：HarnessAgent 响应 → FeishuChannel → 飞书 OpenAPI。支持 7 层路由绑定与 (userId, sessionId) 自动隔离。pom.xml 已添加 agentscope-extensions-channel-common + agentscope-extensions-channel-feishu 依赖。**用户感知**：用户在飞书群里 @机器人 发消息，Agent 直接回复到群里；Agent 主动推送消息到飞书。 | `scope.agentscope.advanced.channel-enabled=true` |
| 25 | A2A 协议 | [A2aConfig](src/main/java/com/agent/scope/framework/config/A2aConfig.java) | ✅ | **一句话**：Agent-to-Agent 标准协议，将本 Agent 导出为 A2A 服务供其他 Agent 调用。**框架里怎么串的**：`A2aConfig` 装配三个 Bean——① `ConfigurableAgentCard`（名称/描述/版本/输入输出模式/JSON-RPC 传输）；② `AgentRunner`（基于 `ReActAgentWithBuilderRunner`，每个 A2A 请求构建独立 ReActAgent，复用主模型与工具容器）；③ `AgentScopeA2aServer`（构建后调 `postEndpointReady()` 暴露 JSON-RPC 端点）。服务端在独立端口 8081 暴露。**用户感知**：其他 Agent 系统通过 A2A 协议调用本 Agent 的推理与工具能力，实现跨服务、跨语言协作。 | `scope.agentscope.advanced.a2a-enabled=true` |
| 26 | MCP 协议 | [McpConfig](src/main/java/com/agent/scope/framework/config/McpConfig.java) | ✅ | **一句话**：从 Nacos 注册中心动态发现 MCP 服务器，将其工具注册为 AgentScope 工具。**框架里怎么串的**：`McpConfig` 从 Spring 环境读取 Nacos 连接信息（serverAddr/namespace/username/password），构建 `NacosMcpServerManager.from(Properties)`。遍历配置的 MCP 服务器列表，为每个服务器通过 `NacosMcpClientBuilder.create(name, manager).asyncClient(true).build()` 创建客户端，调 `initialize().block()` 建立连接，再通过 `NacosMcpToolBuilder.create(client).includeTools().excludeTools().build()` 构建工具列表，逐个调 `toolkit.registerAgentTool()` 注册。支持 Nacos 配置变更热更新（`registerSubscribeMcpClient` 订阅）。pom.xml 已添加 agentscope-extensions-mcp-nacos 依赖。**用户感知**：Agent 能调用 MCP 生态的现成工具（文件系统、搜索、数据库等），无需自己实现。 | `scope.agentscope.advanced.mcp-enabled=true` + `scope.agentscope.mcp.servers` 列表 |
| 27 | Agent as Tool | [AgentAsToolConfig](src/main/java/com/agent/scope/framework/config/AgentAsToolConfig.java) | ✅ | **一句话**：把子 Agent 封装为工具注册到 Toolkit，供父 Agent 通过工具调用委派任务。**框架里怎么串的**：`AgentAsToolConfig` 遍历 `scope.agentscope.subagents` 配置，为每个子 Agent 构建独立 `ReActAgent`（复用主模型与 Toolkit），包装为 `SubAgentTool`（实现 `AgentTool` 接口），通过 `toolkit.registerAgentTool()` 注册。工具名格式为 `agent_<子Agent名称>`（如 agent_vision），支持按名称匹配领域系统提示词。`forwardEvents=false` 避免子 Agent 事件污染父 Agent SSE 流。**用户感知**：主 Agent 调用 `agent_vision` 工具完成图像识别，调用 `agent_knowledge` 工具查询知识库，各 Agent 专注自身领域。 | `scope.agentscope.advanced.agent-as-tool-enabled=true` + `scope.agentscope.subagents` 列表 |
| 28 | 技能系统 | [SkillRepositoryConfig](src/main/java/com/agent/scope/framework/config/SkillRepositoryConfig.java) | ✅ | **一句话**：Agent 能按需加载"技能包"，每个技能是一份写好的能力（SKILL.md + 参考文档 + 脚本）。**框架里怎么串的**：`new FileSystemSkillRepository(skillsDir)` 真实构建并通过 `builder.skillRepository(repo)` 装配到 HarnessAgent。Agent 推理时看得到技能仓库里的 skill 列表，需要哪个就调 `load_skill_through_path` 工具加载详情。支持项目全局 / 工作区 / 用户级四层来源。**用户感知**：团队写一个"代码评审"技能包丢进 skills/，Agent 立刻就能用，不用改代码。 | `scope.agentscope.advanced.skill-repository-enabled=true` |
| 29 | 内置工具 | [BuiltinToolsConfig](src/main/java/com/agent/scope/framework/config/BuiltinToolsConfig.java) | ✅ | **一句话**：框架自带 todo_write 工具，Agent 自己管任务清单。**框架里怎么串的**：`new TodoTools()` + `toolkit.registerTool(toolBean)` 真实注册到 Toolkit。Agent 推理时可以调 `todo_write` 维护结构化任务清单（全量替换，必须恰好一个 in_progress），每轮推理前看到 todos 小提示保持聚焦。**用户感知**：Agent 干复杂任务时会自己拆 todo，不会东一榔头西一棒子。 | `scope.agentscope.builtin-tools.enabled=true` |
| 30 | 会话生命周期 | [SessionLifecycleConfig](src/main/java/com/agent/scope/framework/config/SessionLifecycleConfig.java) + [SessionLifecycleController](src/main/java/com/agent/scope/framework/controller/SessionLifecycleController.java) | ✅ | **一句话**：能主动销毁/列出/检查会话，清理 Redis 残留。**框架里怎么串的**：`SessionLifecycleManager` 封装 StateStore 底层操作（destroySession / listSessions / sessionExists），`SessionLifecycleController` 通过 REST 暴露三个端点——`DELETE /api/session/{userId}/{sessionId}` 销毁、`GET /api/session/{userId}/list` 列出、`GET /api/session/{userId}/{sessionId}/exists` 检查存在性。多租户隔离通过 (userId, sessionId) 二元组自动实现，异常用 ErrorCode.SESSION_DESTROY_FAILED 等自定义异常抛出。**用户感知**：用户退出登录时前端调销毁接口清理会话，管理后台能列出某用户所有会话。 | `scope.agentscope.session.enabled=true`，REST 端点调用 |
| 31 | 多租户隔离 | SessionContext + RuntimeContext | ✅ | **一句话**：不同用户的数据、状态、运行时彻底隔开，互不串味。**框架里怎么串的**：userId + sessionId 二元组五层贯穿——请求层（ChatStreamDTO 字段）、上下文层（RuntimeContext 属性）、运行时层（Reactor Context 传播）、状态层（RedisAgentStateStore 按 (userId, sessionId) 分区）、数据层（chat_message_record.user_id/session_id 索引）。**用户感知**：A 用户查自己的设备列表，绝对不会返回 B 用户的设备；A 的对话历史 B 看不到。 | 请求携带 userId + sessionId |

### 生产级增强特性（32-48）

| 序号 | 特性名称 | 配置类 | 落地 | 实现效果（大白话 + 框架描述） | 使用方式 |
|:---|:---|:---|:---|:---|:---|
| 32 | 沙箱快照与恢复 | [SandboxConfig](src/main/java/com/agent/scope/framework/config/SandboxConfig.java) | ✅ | **一句话**：沙箱环境能存快照、能从快照恢复，进程重启后长任务不丢。**框架里怎么串的**：`SandboxConfig` 采用策略模式 + 注册表按 `sandbox-snapshot-type` 从 `SNAPSHOT_FACTORIES` 映射表查找工厂函数——local（`LocalSnapshotSpec`，快照存工作区 snapshots/ 目录）、oss（降级为 local，需 OSS 扩展包）、noop（`NoopSnapshotSpec`，禁用快照）。快照规范通过 `KubernetesFilesystemSpec.snapshotSpec()` 装配到沙箱文件系统，支持进程重启后从快照恢复长任务状态。**用户感知**：沙箱里跑了一半的脚本，进程重启后能从快照接着跑，不用从头来。 | `scope.agentscope.advanced.sandbox-snapshot-type=local/oss/noop` |
| 33 | 技能自动沉淀 | [SkillPromotionService](src/main/java/com/agent/scope/framework/service/SkillPromotionService.java) | ✅ | **一句话**：Agent 干完复杂任务后自动总结成技能包存下来，下次遇到类似任务直接复用。**框架里怎么串的**：`SkillPromotionService` 在 ChatService 的 `doOnComplete` 回调中异步触发（`@Async`）。沉淀门槛：工具调用次数 ≥ 3 且存在最终回复。从 `ChatSessionRecorder` 提取工具调用链（triggers）、执行步骤（steps）、最终回复摘要（description），构建 `AgentSkill`（name=auto_\<sessionId哈希\>），生成 Markdown + YAML frontmatter 格式技能文件。优先通过 `AgentSkillRepository.save()` 持久化，降级写入工作区 skills/ 目录。所有异常捕获降级为日志，不影响主流程。**用户感知**：Agent 处理过一次"开灯+查设备"的复杂任务后，下次类似任务自动加载沉淀的技能，响应更快更准。 | `scope.agentscope.advanced.skill-promotion-enabled=true` |
| 34 | 分布式后端 | [StateStoreConfig](src/main/java/com/agent/scope/framework/config/StateStoreConfig.java) | ✅ | **一句话**：状态存 Redis，多副本共享同一份状态。**框架里怎么串的**：`RedisAgentStateStore` 真实构建并装配（与特性 6 同一实现），AgentState 序列化后按 (userId, sessionId) 分区存 Redis。任意副本都能加载任意用户的完整上下文。**用户感知**：3 个副本轮询服务，用户请求落到哪个副本都能接着上次聊。 | `scope.agentscope.state-store.type=redis` |
| 35 | PlanNotebook | [PlanModeConfig](src/main/java/com/agent/scope/framework/config/PlanModeConfig.java) | ✅ | **一句话**：计划模式的笔记本组件，通过 PlanModeManager 管理计划文件。**框架里怎么串的**：`PlanModeConfig` 装配 `PlanModeManager` Bean，HarnessAgentConfig 通过 `builder.enablePlanMode()` 启用计划模式（与特性 23 同源）。计划文件持久化到工作区 plans/ 目录，支持 plan_enter / plan_write / plan_exit 三阶段切换，PlanNotebook 在计划模式下自动管理计划文件的创建、更新与完成状态。**用户感知**：Agent 在计划模式下自动维护计划笔记本，用户可查看执行计划的进展与变更。 | `scope.agentscope.advanced.plan-mode-enabled=true` |
| 36 | AG-UI 协议 | [AgUiConfig](src/main/java/com/agent/scope/framework/config/AgUiConfig.java) | ✅ | **一句话**：AG-UI 协议适配，桥接 AgentScope 与 AG-UI 兼容前端（如 CopilotKit）。**框架里怎么串的**：`AgUiConfig` 装配三个 Bean——① `AguiAdapterConfig`（工具合并模式 AGENT_ONLY、状态事件开启、推理事件开启、超时 5 分钟）；② `AguiAgentAdapter`（把 HarnessAgent 包装为 AG-UI 适配器，AgentScope AgentEvent → AG-UI AguiEvent 事件映射）；③ `AguiRequestProcessor`（通过 `AgentResolver` 将前端请求路由到 HarnessAgent，解析 RunAgentInput）。pom.xml 已添加 agentscope-extensions-agui 依赖。**用户感知**：AG-UI 兼容前端（如 CopilotKit）直接对接 Agent，实时展示推理过程、工具调用与状态同步。 | `scope.agentscope.advanced.ag-ui-enabled=true` |
| 37 | 异步工具执行 | AgentScope 核心 | ✅ | **一句话**：长耗时工具异步跑，不阻塞主线程。**框架里怎么串的**：Reactor Flux 响应式执行——`harnessAgent.streamEvents().doOnNext().doOnComplete().subscribe()` 异步驱动，工具在 `boundedElastic` 线程池执行，事件流通过 Reactor 调度回 SSE 线程。**用户感知**：调一个 30 秒的工具，前端能实时看到"开始执行"→"执行中"→"完成"的进度，不会卡死。 | 自动支持 |
| 38 | 定时唤醒调度 | [SchedulerConfig](src/main/java/com/agent/scope/framework/config/SchedulerConfig.java) + [SchedulerController](src/main/java/com/agent/scope/framework/controller/SchedulerController.java) | ✅ | **一句话**：让 Agent 定时干活（每天 8 点发日报），基于 Quartz 调度器实现。**框架里怎么串的**：`SchedulerConfig` 装配 `QuartzAgentScheduler`（autoStart=true，启动即生效），`SchedulerController` 暴露三个 REST 端点——`POST /api/scheduler/schedule` 注册调度任务（CRON/fixedRate/fixedDelay 三选一）、`GET /api/scheduler/list` 列举所有任务、`POST /api/scheduler/{taskId}/cancel` 取消任务。被唤醒的 Agent 模型配置复用服务端 DashScope 配置，调用方只需提供任务名称、调度模式与触发消息。`@PreDestroy` 关闭调度器释放 Quartz 线程池。pom.xml 已添加 agentscope-extensions-scheduler-quartz 依赖。**用户感知**：配置"每天早 8 点检查设备状态并告警"，Agent 自动定时执行，无需人工触发。 | `scope.agentscope.advanced.scheduler-enabled=true`，REST 端点调用 |
| 39 | OpenTelemetry | [MiddlewareChainConfig](src/main/java/com/agent/scope/framework/config/MiddlewareChainConfig.java) | ✅ | **一句话**：每次调用都打 trace 上报 Zipkin，全链路可追踪。**框架里怎么串的**：`new OtelTracingMiddleware()` 加入中间件链；onAgent / onModelCall / onActing 三阶段打点，span 名为 `invoke_agent` / `chat` / `execute_tool`；Zipkin 端点配置就绪，trace context 跨工具调用传播。**用户感知**：在 Zipkin UI 看到一次对话从 Controller → Agent → 模型 → 工具的完整调用链和耗时。 | `scope.agentscope.advanced.otel-tracing-enabled=true` |
| 40 | Studio 可视化 | [StudioConfig](src/main/java/com/agent/scope/framework/config/StudioConfig.java) + [StudioController](src/main/java/com/agent/scope/framework/controller/StudioController.java) | ✅ | **一句话**：把已落库的调试数据通过 HTTP 端点暴露，可视化界面能回放对话、看工具调用、查 Token 消耗。**框架里怎么串的**：`StudioController` 暴露 5 个端点——`GET /api/studio/sessions/{sessionId}/messages` 查对话消息、`/tool-calls` 查工具调用、`/model-calls` 查模型调用、`/token-usage` 查 Token 明细、`GET /api/studio/token-summary` 聚合统计全部会话 Token。所有端点用 `BusinessConst.RESPONSE_KEY_CODE` 等常量返回，仅在 `scope.agentscope.advanced.studio-enabled=true` 时生效。**用户感知**：开发环境调通对话后，浏览器访问 Studio 端点能看到完整推理过程回放、工具调用链路、Token 成本核算。 | `scope.agentscope.advanced.studio-enabled=true`，REST 端点调用 |
| 41 | 工具超时控制 | [ToolEnhancementConfig](src/main/java/com/agent/scope/framework/config/ToolEnhancementConfig.java) | ✅ | **一句话**：每个工具调用都有超时保护，卡住就中断。**框架里怎么串的**：`Duration` Bean 用于构造 `ToolEnhancementMiddleware` 接入中间件链；onActing 阶段包裹工具调用，超过 `scope.agentscope.tool.timeout-ms` 自动 cancel Mono 并返回超时错误。**用户感知**：调 HDL 接口卡死 30 秒后自动中断，Agent 收到超时错误重新规划，不会无限等。 | `scope.agentscope.tool.timeout-ms=30000` |
| 42 | 工具结果缓存 | [ToolEnhancementConfig](src/main/java/com/agent/scope/framework/config/ToolEnhancementConfig.java) | ✅ | **一句话**：高频查询结果缓存到 Redis，省时间省 Token。**框架里怎么串的**：`ToolResultCache`（Redis-based，含 get / put / evict）用于构造 `ToolEnhancementMiddleware` 接入链；工具执行前先查缓存，命中直接返回（标记 cache hit），未命中才真执行并写回缓存。**用户感知**：5 秒内连续问"查设备列表"，第二次直接走缓存毫秒返回，不重复调 HDL 接口。 | `scope.agentscope.tool.cache-ttl-seconds=300` |
| 43 | 限流与熔断 | [ResilienceMiddleware](src/main/java/com/agent/scope/framework/middleware/ResilienceMiddleware.java) + [RedisRateLimiterService](src/main/java/com/agent/scope/framework/service/RedisRateLimiterService.java) | ✅ | **一句话**：双层限流——单机 Resilience4j + 分布式 Redis Lua，防刷接口、防模型雪崩。**框架里怎么串的**：① Resilience4j 层：`RateLimiter` + `CircuitBreaker` 装配到中间件链，nacos 配置 `modelCallRateLimiter` / `modelCallCircuitBreaker` 实例，模型调用 QPS 超限自动拒绝、连续失败自动熔断；② Redis Lua 层：`RedisRateLimiterService` 实现滑动窗口限流（Lua 脚本保证原子性），支持 model-call / hdl-api / user-session 三维度，`ChatService.streamEvents` 和 `confirmAndResume` 入口调 `tryAcquireUserSession(userId, sessionId)` 拦截刷接口请求。**用户感知**：用户狂点发送按钮，超过 10 req/s 的请求被限流返回 429；模型连续报错时熔断器打开，保护下游服务。 | `resilience4j.*` + `scope.agentscope.redis-rate-limit.*` |
| 44 | 提示词模板 | [PromptTemplateConfig](src/main/java/com/agent/scope/framework/config/PromptTemplateConfig.java) | ✅ | **一句话**：系统提示词存 Nacos，改了不用重启服务。**框架里怎么串的**：`PromptTemplateHolder` Bean 持有从 Nacos 动态加载的系统提示词；HarnessAgentConfig 第 165-168 行优先从 holder 读取，回退 `DEFAULT_SYSTEM_PROMPT` 内置模板；`@RefreshScope` 支持 Nacos 热更新，配置变更 30 秒内 Bean 自动重建。**用户感知**：在 Nacos 改提示词，30 秒后新对话就用新提示词，老对话不受影响。 | `scope.agentscope.prompt-templates.enabled=true` |
| 45 | 可观测性增强 | [ObservabilityConfig](src/main/java/com/agent/scope/framework/config/ObservabilityConfig.java) | ✅ | **一句话**：调用次数、延迟、Token 消耗都打成 Prometheus 指标，Grafana 能看图。**框架里怎么串的**：Counter / Timer Bean 注册到 `MeterRegistry`；`ObservabilityMiddleware` 接入中间件链，onAgent 阶段记调用次数和耗时、onModelCall 阶段记 Token 消耗；`/actuator/prometheus` 端点暴露指标。**用户感知**：Grafana 配 Prometheus 数据源，能看到 QPS、P99 延迟、Token 消耗趋势图，异常时告警。 | `scope.agentscope.observability.enabled=true` |
| 46 | 配置版本管理 | [ConfigVersionConfig](src/main/java/com/agent/scope/framework/config/ConfigVersionConfig.java) + [ConfigVersionController](src/main/java/com/agent/scope/framework/controller/ConfigVersionController.java) | ✅ | **一句话**：能查 Nacos 配置历史版本、能回滚到任意版本。**框架里怎么串的**：`ConfigVersionManager` 集成 Nacos OpenAPI——用 `RestTemplate` + Basic Auth 调 `/nacos/v1/cs/history` 接口。Nacos 连接信息从 Spring Environment 读取（`spring.cloud.nacos.config.*`），与应用共享同一 Nacos 实例。`ConfigVersionController` 暴露 REST 端点——查历史版本列表、查特定版本内容、回滚到指定版本。异常用 `ErrorCode.CONFIG_VERSION_QUERY_FAILED` 等自定义异常抛出。**用户感知**：配置改坏了，调接口查历史版本一键回滚，不用登 Nacos 控制台。 | `scope.agentscope.config-version.enabled=true`，REST 端点调用 |
| 47 | Agent 健康检查 | [HealthCheckConfig](src/main/java/com/agent/scope/framework/config/HealthCheckConfig.java) | ✅ | **一句话**：定时探活 Redis 和 MySQL，挂了立刻告警。**框架里怎么串的**：`redisHealthIndicator` 真实 ping Redis；`mysqlHealthIndicator` 真实 `getConnection()` 测连接；Spring Boot Actuator `/actuator/health` 自动聚合暴露。K8s livenessProbe / readinessProbe 配这个端点。**用户感知**：Redis 挂了 K8s 自动重启 Pod，前端健康检查页能看到具体哪个组件挂了。 | `scope.agentscope.health-check.enabled=true` |
| 48 | 任务队列 | [TaskQueueConfig](src/main/java/com/agent/scope/framework/config/TaskQueueConfig.java) + [TaskQueueController](src/main/java/com/agent/scope/framework/controller/TaskQueueController.java) | ✅ | **一句话**：耗时任务投队列异步跑，支持 Agent 推理任务，立即返回 taskId 通过轮询取结果。**框架里怎么串的**：`TaskQueueManager` 基于 `LinkedBlockingQueue` + `ThreadPoolExecutor`（4 worker / 容量 100 / CallerRunsPolicy 拒绝策略 / daemon 线程）实现内存任务队列——`submitTask(taskType, payload)` 投递任务返回 taskId，Worker 线程池消费执行，`getTaskStatus(taskId)` 查 PENDING/RUNNING/COMPLETED/FAILED 状态，`getTaskResult(taskId)` 取结果。注入 `HarnessAgent` 执行回调（`buildAgentExecutor`），`taskType=agent` 时通过 `streamEvents` 收集 `TextBlockDeltaEvent` 增量文本拼接为最终回复，独立 sessionId 隔离异步任务。`TaskQueueController` 暴露三个 REST 端点——`POST /api/task/submit` 提交任务、`GET /api/task/{taskId}/status` 查状态、`GET /api/task/{taskId}/result` 取结果，参数校验用 `ErrorCode.TASK_PARAM_INVALID` / `TASK_NOT_FOUND` / `TASK_RESULT_NOT_READY` 自定义异常抛出。`@PreDestroy` 优雅关闭线程池。**用户感知**：提交"生成日报"等耗时任务，立即返回 taskId，通过轮询获取结果，不阻塞主线程；taskType=agent 时 Agent 在后台异步推理，结果就绪后客户端取回完整回复。 | `scope.agentscope.advanced.task-queue-enabled=true`，REST 端点调用 |

### 落地统计

| 状态 | 数量 | 占比 | 特性序号 |
|:---|:---|:---|:---|
| ✅ 已落地 | 48 | 100% | 1-48 全部 |
| ⚠️ 部分落地 | 0 | 0% | - |
| ❌ 未落地 | 0 | 0% | - |

**全量落地说明**：
48 项特性全部完成企业级实战落地，覆盖以下能力域：
1. **核心 Agent 与执行链路**（1-15）：ReActAgent + HarnessAgent 双层架构、RuntimeContext、状态持久化、事件流、SSE 流式输出、结构化输出、多模态、中间件五阶段钩子、权限系统、HITL 人机交互
2. **生产级容错与压缩**（16-17）：模型容错 fallback、上下文压缩 + 溢出兜底
3. **工程基础设施**（18-23）：工作区、分布式记忆、文件系统、K8s 沙箱、子 Agent、计划模式
4. **分布式与协作**（24-31）：飞书 Channel、A2A 协议、MCP 协议、Agent as Tool、技能仓库、内置工具、会话生命周期、多租户隔离
5. **企业增强能力**（32-48）：沙箱快照恢复、技能自动沉淀、分布式状态后端、PlanNotebook、AG-UI 协议、异步工具、Quartz 定时调度、OpenTelemetry、Studio 可视化、工具超时/缓存、双层限流熔断、提示词模板热更新、可观测性、配置版本管理、健康检查、任务队列

**生产环境部署注意事项**：
- 多数特性采用 `@ConditionalOnProperty` 条件装配，需在 Nacos 显式开启对应开关后才生效
- **任务队列（48）**：当前为单节点内存队列（LinkedBlockingQueue + ThreadPoolExecutor），多副本部署如需跨节点调度需替换为 RabbitMQ/Kafka 后端（加 `spring-boot-starter-amqp` / `spring-kafka` 依赖）
- **沙箱（21/32）**：依赖 K3s 集群，需提前配置 namespace / serviceAccount / 容器镜像
- **MCP（26）/ Channel（24）/ A2A（25）/ AG-UI（36）**：依赖对应扩展包，pom.xml 已声明依赖
- **定时调度（38）**：基于 Quartz 内存调度，多副本部署需配置数据库存储的 Quartz JobStore 防止重复触发

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

> **双层限流说明**：① Resilience4j 层（单机）—— RateLimiter / CircuitBreaker 装配到中间件链，按 modelCallRateLimiter / modelCallCircuitBreaker 实例限流熔断；② Redis Lua 层（分布式）—— RedisRateLimiterService 在 ChatService.streamEvents / confirmAndResume 入口调 `tryAcquireUserSession(userId, sessionId)` 拦截刷接口请求，Lua 脚本保证滑动窗口原子性。两层互补：Resilience4j 防单机过载，Redis Lua 防分布式刷接口。

---

## API 接口

### 对话接口

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 流式对话 | POST | `/api/chat/stream` | SSE 流式推送 Agent 执行过程 |
| 中断执行 | POST | `/api/chat/interrupt` | 按 userId + sessionId 中断 Agent（per-session，双机制兜底） |
| 权限确认 | POST | `/api/chat/confirm` | HITL 暂停后恢复 Agent 执行 |

### 会话管理接口（特性30）

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 销毁会话 | DELETE | `/api/session/{userId}/{sessionId}` | 清理 Redis 中的会话状态 |
| 列出会话 | GET | `/api/session/{userId}/list` | 列出用户所有会话 ID |
| 检查存在性 | GET | `/api/session/{userId}/{sessionId}/exists` | 检查会话是否存在 |

### Studio 调试接口（特性40）

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 对话消息 | GET | `/api/studio/sessions/{sessionId}/messages` | 查询会话消息记录（限 50 条） |
| 工具调用 | GET | `/api/studio/sessions/{sessionId}/tool-calls` | 查询会话工具调用记录（限 50 条） |
| 模型调用 | GET | `/api/studio/sessions/{sessionId}/model-calls` | 查询会话模型调用记录（限 50 条） |
| Token 明细 | GET | `/api/studio/sessions/{sessionId}/token-usage` | 查询会话 Token 消耗（限 20 条） |
| Token 汇总 | GET | `/api/studio/token-summary` | 聚合统计全部会话 Token 消耗 |

### 配置版本管理接口（特性46）

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 历史版本列表 | GET | `/api/config/version/history` | 查询 Nacos 配置历史版本（pageNo, pageSize） |
| 版本详情 | GET | `/api/config/version/{nid}` | 获取特定版本内容 |
| 版本回滚 | POST | `/api/config/version/{nid}/rollback` | 回滚到指定版本 |
| 环境信息 | GET | `/api/config/version/environment` | 获取当前 Nacos 连接环境信息 |

### 定时调度接口（特性38）

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 注册调度任务 | POST | `/api/scheduler/schedule` | 注册 CRON/fixedRate/fixedDelay 三选一调度任务 |
| 列举调度任务 | GET | `/api/scheduler/list` | 列出所有已注册的调度任务 |
| 取消调度任务 | POST | `/api/scheduler/{taskId}/cancel` | 取消指定调度任务 |

### 任务队列接口（特性48）

| 接口 | 方法 | 路径 | 说明 |
|:---|:---|:---|:---|
| 提交异步任务 | POST | `/api/task/submit` | 投递任务到队列，立即返回 taskId（taskType=agent 走 Agent 推理） |
| 查询任务状态 | GET | `/api/task/{taskId}/status` | 查询 PENDING/RUNNING/COMPLETED/FAILED 状态 |
| 获取任务结果 | GET | `/api/task/{taskId}/result` | 获取任务最终结果（仅 COMPLETED 状态有值） |

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
| **Redis** | RedisRateLimiterService | Redis + Lua 滑动窗口分布式限流（特性43，user-session 维度接入 ChatService） |
| **Redis** | HealthCheckConfig | Redis 健康检查探针 |
| **Qdrant** | QdrantProperties | 向量数据库，RAG 知识库语义检索（配置就绪，扩展集成待接入） |
| **MinIO** | FilesystemConfig | 对象存储后端，`scope.agentscope.advanced.filesystem-type=minio` 时作为 Agent 文件系统（特性20） |

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
├── controller/         # 控制器（Chat / Interrupt / SessionLifecycle / Studio / ConfigVersion / Scheduler / TaskQueue）
├── dto/                # 数据传输对象（ChatStreamDTO / PermissionConfirmDTO）
├── entity/             # 实体类（4 张表）
├── enums/              # 枚举（AgentEventEnum / ImageTypeEnum / MediaTypeEnum）
├── exception/          # 自定义异常（BusinessException + 5 个模块异常 + ErrorCode 枚举）
├── handler/            # 事件处理器（策略模式：AgentEventHandler 接口 + 14 个具体处理器 + Registry）
├── mapper/             # MyBatis-Plus Mapper（4 个）
├── middleware/         # 中间件（Resilience / Observability / ToolEnhancement）
├── service/            # 服务（ChatService / ChatRecordService / RedisRateLimiterService / PendingConfirmationService / SkillPromotionService）
├── tool/               # 业务工具（DeviceTool / HomeTool / ProductTool）
├── utils/              # 工具类
└── vo/                 # 值对象（ToolResultVO）
```

---

## License

MIT License
