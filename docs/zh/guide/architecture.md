# 架构

## 模块概览

```
kairo-parent
├── kairo-bom                  — BOM 依赖版本管理
├── kairo-api                  — SPI 接口层（零实现依赖）
├── kairo-core                 — 核心运行时（ReAct 引擎、压缩管道、模型提供者）
├── kairo-tools                — 内置工具集（21 个工具）
├── kairo-mcp                  — MCP 协议集成（StreamableHTTP）
├── kairo-multi-agent          — 多 Agent 编排（A2A 协议、Team、TaskBoard）
├── kairo-observability        — OpenTelemetry 集成
├── kairo-spring-boot-starter  — Spring Boot 自动装配
└── kairo-examples             — 示例应用
```

## 模块说明

### kairo-api

SPI 接口层，**零实现依赖**。定义所有扩展点：`ModelProvider`、`Tool`、`MemoryStore`、`Skill`、`Hook` 等。依赖此模块编写扩展，无需引入完整运行时。

### kairo-core

核心运行时引擎，包含：
- **ReAct 引擎** — `DefaultReActAgent` 实现 Thought→Action→Observation 循环
- **6 级上下文压缩** — 渐进式管道（Snip → Micro → Collapse → Auto → Partial → 熔断器）
- **模型提供者** — 原生 Anthropic 集成 + OpenAI 兼容适配器

### kairo-tools

21 个内置工具，按类别组织：
- **文件操作** — Read、Write、Edit、Glob、Grep
- **执行** — Bash、Monitor
- **交互** — AskUser
- **技能** — SkillList、SkillLoad
- **Agent 操作** — Spawn、Message、Task、Team、Plan

### kairo-mcp

MCP（Model Context Protocol）协议集成，通过 StreamableHTTP + Elicitation Protocol 连接外部工具服务器。

### kairo-multi-agent

多 Agent 编排层：
- **A2A 协议** — Google ADK 兼容的 Agent-to-Agent 通信
- **TaskBoard** — 基于 DAG 的任务依赖追踪
- **TeamScheduler** — 团队协作编排
- **MessageBus** — 进程内发布/订阅消息

### kairo-observability

OpenTelemetry 集成，提供 Agent 执行过程中的追踪、指标和日志。

### kairo-spring-boot-starter

Spring Boot 自动装配。添加 starter 并通过 `application.yml` 配置 — 最少代码即可使用 Agent。
