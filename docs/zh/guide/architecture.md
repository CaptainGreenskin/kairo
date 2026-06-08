# 架构

## 模块概览

31 个叶子模块，按三个 reactor-only 聚合器组织（`kairo-capabilities` / `kairo-transports` / `kairo-starters`）。基础模块仍位于顶层。

```
kairo-parent
├── kairo-bom                       — BOM 依赖版本管理
├── kairo-api                       — SPI 接口层（零实现依赖）
├── kairo-core                      — 核心运行时（ReAct、压缩、模型提供者）
│
├── kairo-capabilities/             — 垂直能力组群（12 个模块）
│   ├── kairo-tools                 — 内置工具集
│   ├── kairo-mcp                   — MCP 协议集成
│   ├── kairo-multi-agent           — A2A 协议 + 团队协调
│   ├── kairo-skill                 — Markdown 技能注册表与加载器
│   ├── kairo-evolution             — 自进化管道 + 治理
│   ├── kairo-expert-team           — plan/generate/evaluate 协调器
│   ├── kairo-observability         — OpenTelemetry exporter
│   ├── kairo-security-pii          — PII 脱敏 + JDBC 审计 + 合规
│   ├── kairo-plugin                — 插件系统（兼容 Claude Code 格式）
│   ├── kairo-cron                  — 定时任务调度器
│   ├── kairo-gateway               — 多 Channel 编排层（路由/会话/流式/镜像）
│   ├── kairo-lsp                   — LSP 诊断子系统
│   └── kairo-acp                   — Agent Client Protocol
│
├── kairo-transports/               — I/O 边界组群（4 个模块）
│   ├── kairo-event-stream          — KairoEventBus 过滤 + 背压
│   ├── kairo-event-stream-sse      — SSE 传输
│   ├── kairo-event-stream-ws       — WebSocket 传输
│   └── kairo-channel-dingtalk      — 钉钉 webhook + 签名验证器
│
├── kairo-starters/                 — Spring Boot starter 组群（13 个模块）
│   ├── kairo-spring-boot-starter-core
│   ├── kairo-spring-boot-starter-mcp
│   ├── kairo-spring-boot-starter-multi-agent
│   ├── kairo-spring-boot-starter-evolution
│   ├── kairo-spring-boot-starter-expert-team
│   ├── kairo-spring-boot-starter-event-stream
│   ├── kairo-spring-boot-starter-gateway
│   ├── kairo-spring-boot-starter-channel-dingtalk
│   ├── kairo-spring-boot-starter-observability
│   ├── kairo-spring-boot-starter-plugin
│   ├── kairo-spring-boot-starter-cron
│   ├── kairo-spring-boot-starter-lsp
│   └── kairo-spring-boot-starter-acp
│
└── kairo-examples                  — 示例应用
```

每个聚合器自身的 `<dependencies>` 为空，从不出现在运行时类路径上；每个叶子模块仍直接继承 `kairo-parent`。

## 模块说明

### kairo-api

SPI 接口层，**零实现依赖**。定义所有扩展点：`ModelProvider`、`Tool`、`MemoryStore`、`Skill`、`Hook` 等。依赖此模块编写扩展，无需引入完整运行时。

### kairo-core

核心运行时引擎，包含：
- **ReAct 引擎** — `DefaultReActAgent` 实现 Thought→Action→Observation 循环
- **6 级上下文压缩** — 渐进式管道（Snip → Micro → Collapse → Auto → Partial → 熔断器）
- **模型提供者** — 原生 Anthropic 集成 + OpenAI 兼容适配器

### kairo-tools

56 个内置工具，按类别组织：
- **文件操作** — Read、Write、Edit、Glob、Grep
- **执行** — Bash、Monitor
- **交互** — AskUser
- **技能** — SkillList、SkillLoad、SkillManage
- **Agent 操作** — Spawn、Message、Team、Plan

### kairo-mcp

MCP（Model Context Protocol）协议集成，通过 StreamableHTTP + Elicitation Protocol 连接外部工具服务器。

### kairo-multi-agent

多 Agent 编排层：
- **A2A 协议** — Google ADK 兼容的 Agent-to-Agent 通信
- **TeamCoordinator SPI** — 可插拔的团队编排契约（ADR-016），默认实现为 expert-team 编排器（plan → generate → evaluate）
- **MessageBus** — 进程内发布/订阅消息

### kairo-observability

OpenTelemetry 集成以分布式追踪为核心（span 树与属性/事件）。

### kairo-spring-boot-starter-*（按特性拆分）

Spring Boot 自动装配按特性拆分为 13 个 starter，置于 `kairo-starters/` 下。从 `kairo-spring-boot-starter-core` 开始，按需添加 `-mcp`、`-multi-agent`、`-evolution`、`-expert-team`、`-event-stream`、`-gateway`、`-channel-dingtalk`、`-observability`、`-plugin`、`-cron`、`-lsp`、`-acp`。
