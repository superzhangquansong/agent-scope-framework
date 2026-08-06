# 技能定义：企业级 AgentScope 多智能体工程构建（完整版 v2.2.0）

> **技能 ID**：`agentscope-enterprise-builder`  
> **版本**：v1.0.0  
> **适用角色**：后端架构师 / Java 高级开发工程师 / Agent 高级开发工程师 / 前端架构师  
> **前置依赖**：Java 22+（推荐 Java 22）, Spring Boot 4.x, Nacos, Redis, MySQL, Qdrant, MinIO, Maven 3.9+  
> **交付形态**：完整前后端分离项目源码 + 运维脚本 + 技术文档  
> **框架版本**：AgentScope Java 2.0.0 GA（2026-07-10 正式发布）

---

## 1. 技能目标

基于 AgentScope Java 2.0 GA 官方框架，从零构建一套**企业级分布式多智能体应用系统**。AgentScope Java 2.0 是面向分布式部署、稳定运行、权限安全等企业级需求全面升级的生产级框架，标志着 AgentScope Java 彻底从「原型式透明开发」迈入「企业级智能体系统工程」新阶段。该系统需在高并发、分布式环境下稳定运行，支持智能设备控制、产品检索、场景编排等复杂业务，实现毫秒级响应与配置秒级热更新。

AgentScope Java 2.0 的核心设计思路是**双层 Agent 架构**：

- **ReActAgent**：无状态推理核心，提供“推理 → 工具调用 → 响应”的 ReAct 循环。Agent 实例完全无状态，所有可变状态通过 Reactor Context 传播，单实例可安全服务多个 `(userId, sessionId)` 组合。
- **HarnessAgent**：通过 Middleware 和 Toolkit 通道扩展 ReActAgent，增加 Workspace、记忆、沙箱、子 Agent、技能和计划模式等工程基础设施——核心推理循环得以保留，仅做增强。
---

## 2. 输入（Inputs）—— 需求规格定义

### 2.1 工程基础约束

| 类别 | 规格说明                                                             |
| :--- |:-----------------------------------------------------------------|
| **编码规范** | 严格遵循 **阿里巴巴 Java 开发手册（泰山版）**                                     |
| **注释要求** | 关键类、方法及复杂逻辑需提供完整中文注释，覆盖率 ≥ 80%                                   |
| **根目录限制** | 项目运行期间**严禁**生成 `.agentscope` 本地文件夹                               |

### 2.2 AgentScope 官方特性全量实现清单（共 48 项）

- **AgentScope Java 2.0 GA 官方接口文档地址**：[https://java.agentscope.io/v2/zh/docs/index.html](https://java.agentscope.io/v2/zh/docs/index.html) ，详细内容请阅读所有章节。
- 必须按当前代码风格完整覆盖 AgentScope Java 2.0 GA 官方所有核心特性及生产级增强特性。所有特性单独编写各自的 `XxxConfig.java` 文件，最终统一在 `AgentScopeConfig.java` 中装配所有特性。

#### 2.2.1 核心基础特性（1-40）

| 序号 | 核心功能模块 | 强制要求 |
| :--- | :--- | :--- |
| 1 | **智能体（Agent）** | 支持 `ReActAgent` 基类继承与自定义扩展 |
| 2 | **HarnessAgent 入口** | 必须使用 HarnessAgent 作为推荐入口，打包 Workspace、长期记忆、会话持久化、子 Agent、沙箱等工程能力 |
| 3 | **多用户/多会话并发** | Agent 实例完全无状态，通过 Reactor Context 传播可变状态，单实例安全服务多个 `(userId, sessionId)` 组合 |
| 4 | **RuntimeContext** | 每次调用携带轻量级上下文，用于参数传递与提速 |
| 5 | **中断执行（Interrupt）** | 支持运行时中断及基于状态存储的断点恢复 |
| 6 | **状态持久化（AgentStateStore）** | 持久化 Agent 运行状态至数据库，按 `(userId, sessionId)` 自动分区 |
| 7 | **ContentBlock 统一消息模型** | 文本、图片、音频、工具结果、模型思考统一收敛到 `ContentBlock`（TextBlock / DataBlock / ToolUseBlock / ToolResultBlock / HintBlock 等） |
| 8 | **事件流系统（Event Stream）** | `streamEvents()` 发射 **28 种类型化 AgentEvent**，使 Agent 执行过程可观测、可交互、可中断 |
| 9 | **流式输出（Streaming）** | 采用 SSE 或 WebSocket 实现逐字/逐句推送 |
| 10 | **结构化输出** | 定义顶级 `BaseResponse`，子业务继承扩展，统一返回格式；支持自纠正输出解析器 |
| 11 | **多模态（Multimodal）** | 支持文本、图片、音频的输入与输出，通过统一 `DataBlock` 支持 |
| 12 | **中间件（Middleware）** | 五阶段洋葱+管道混合模型（`onAgent` / `onReasoning` / `onActing` / `onModelCall` / `onSystemPrompt`） |
| 13 | **Hook 系统** | 在 `onAgent` / `onReasoning` / `onActing` / `onModelCall` / `onSystemPrompt` 五个生命周期阶段插入逻辑 |
| 14 | **权限系统（Permission）** | 三态决策机制：**允许 / 需用户批准 / 拒绝**，基于静态规则、工具类型和输入内容分析 |
| 15 | **人机交互（HITL）** | 在“下单”等敏感链路强制插入人工确认/拒绝节点，支持精确恢复 |
| 16 | **模型容错** | 统一 Credential + ModelRegistry 抽象，支持可配置最大重试次数和备用模型——主模型不可用时自动故障转移 |
| 17 | **上下文压缩** | 结构化压缩保留任务目标、当前状态、关键发现和后续步骤，支持 MapReduce 或滑动窗口策略 |
| 18 | **工作区（Workspace）** | Agent 执行时拥有独立临时工作目录，支持跨 Agent 共享 |
| 19 | **分布式记忆（Memory）** | 用户级长期记忆，基于 Redis + DB 实现跨节点共享 |
| 20 | **文件系统（FileSystem）** | 读写本地文件或 MinIO 对象存储 |
| 21 | **沙箱（Sandbox）** | 安全执行用户上传的 Python/Shell 脚本（如 Code Interpreter），支持快照与恢复 |
| 22 | **子 Agent（Sub-Agent）** | 父 Agent 委派任务给子 Agent 并行执行，最后汇总结果 |
| 23 | **计划模式（Plan Mode）** | 复杂任务先规划（Plan），再拆分执行（Execute），通过 PlanNotebook 管理 |
| 24 | **Channel 通信** | Agent 间基于消息队列或事件总线的异步通信，支持钉钉、飞书、企业微信等 Channel 模块 |
| 25 | **A2A 协议（Agent-to-Agent）** | 通过 Nacos 等服务注册实现分布式多智能体互发现与互调用，支持异构 Agent 互通 |
| 26 | **MCP（模型上下文协议）** | 支持通过标准化协议调用外部工具和数据源（如 GitHub、Filesystem 等 MCP 服务） |
| 27 | **Agent as Tool** | 支持将一个 Agent 封装为工具供其他 Agent 调用 |
| 28 | **技能系统（Skills）** | 支持本地上传 ZIP 或远程 Git 仓库动态加载 |
| 29 | **内置工具（Tools）** | 提供通用工具（时间计算、JSON 解析等） |
| 30 | **会话生命周期管理** | 支持会话的创建、销毁、超时、跨节点迁移 |
| 31 | **多租户组织级隔离** | 支持 `session` / `user` / `agent` / `org` 多维度的状态与数据隔离 |
| 32 | **沙箱快照与恢复** | 支持沙箱状态快照，进程重启后可恢复长任务 |
| 33 | **技能自动沉淀** | 成功执行的任务模式自动生成 Markdown Skill 并保存至 `workspace/skills/`，跨会话共享 |
| 34 | **分布式后端（DistributedBackend）** | 一行配置即可启用分布式后端（Redis/MySQL/PostgreSQL/OSS/COS），支持跨副本会话恢复、沙箱状态快照 |
| 35 | **PlanNotebook（计划笔记本）** | 结构化任务管理系统，将复杂目标分解为有序、可追踪的步骤 |
| 36 | **AG-UI 协议适配** | 覆盖标准化前端渲染需求，前端 UI 与 Agent 事件流的标准化对接 |
| 37 | **异步工具执行** | 支持长耗时工具的异步化执行，不阻塞 Agent 主循环 |
| 38 | **定时唤醒调度** | 支持周期任务调度，Agent 可定时唤醒执行 |
| 39 | **OpenTelemetry 集成** | 原生集成 OpenTelemetry，支持分布式链路追踪 |
| 40 | **AgentScope Studio 可视化调试** | 提供可视化调试与实时监控能力 |
| 41 | **工具执行超时控制** | 为每个 `@Tool` 方法配置独立超时（如 `@Tool(timeout=5000)`），防止卡死 Agent 主循环 |
| 42 | **工具结果缓存** | 对高频查询（如设备列表、产品价格）支持本地缓存或 Redis 缓存，减少重复调用后端 API，降低延迟 |
| 43 | **限流与熔断** | 集成 Resilience4j 或 Sentinel，对模型调用和外部 API 调用进行限流，防止突发流量压垮系统 |
| 44 | **提示词模板管理** | 支持从数据库或 Nacos 动态加载系统提示词模板，方便运营人员调整 Prompt 而无需重启 |
| 45 | **模型调用可观测性增强** | 除了 OpenTelemetry，增加 Prometheus 指标（如调用次数、延迟分布、Token 消耗），便于 Grafana 监控告警 |
| 46 | **配置版本管理** | Nacos 配置支持回滚和版本对比，便于问题定位 |
| 47 | **Agent 健康检查** | 提供 `/actuator/health` 端点，包含各组件（Redis、MySQL、Qdrant、MinIO、模型 API）的状态探针，用于 K8s 调度 |
| 48 | **任务队列与异步调度** | 对长耗时任务（如批量控制）可放入消息队列（RabbitMQ/Kafka）异步执行，避免 HTTP 请求超时 |