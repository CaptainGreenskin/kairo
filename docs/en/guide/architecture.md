# Architecture

## Module Overview

31 leaf modules grouped under three reactor-only aggregators (`kairo-capabilities` / `kairo-transports` / `kairo-starters`). Foundation modules stay flat at the top.

```
kairo-parent
├── kairo-bom                       — BOM for dependency version management
├── kairo-api                       — SPI interface layer (zero implementation deps)
├── kairo-core                      — Core runtime (ReAct, compaction, providers)
│
├── kairo-capabilities/             — vertical capability cohort (12 modules)
│   ├── kairo-tools                 — built-in tool suite
│   ├── kairo-mcp                   — MCP protocol integration
│   ├── kairo-multi-agent           — A2A protocol + team coordination
│   ├── kairo-skill                 — Markdown skill registry & loader
│   ├── kairo-evolution             — self-evolution pipeline + governance
│   ├── kairo-expert-team           — plan/generate/evaluate coordinator
│   ├── kairo-observability         — OpenTelemetry exporter
│   ├── kairo-security-pii          — PII redaction + JDBC audit + compliance
│   ├── kairo-plugin                — Plugin system (Claude Code format compatible)
│   ├── kairo-cron                  — Scheduled task scheduler
│   ├── kairo-gateway               — Multi-channel routing / session / streaming / mirror
│   ├── kairo-lsp                   — LSP diagnostics subsystem (post-edit baseline diff)
│   └── kairo-acp                   — Agent Client Protocol (editor integration via JSON-RPC over stdio)
│
├── kairo-transports/               — I/O boundary cohort (4 modules)
│   ├── kairo-event-stream          — KairoEventBus filtering + backpressure
│   ├── kairo-event-stream-sse      — SSE transport
│   ├── kairo-event-stream-ws       — WebSocket transport
│   └── kairo-channel-dingtalk      — DingTalk webhook + signature verifier
│
├── kairo-starters/                 — Spring Boot starter cohort (13 modules)
│   ├── kairo-spring-boot-starter-core
│   ├── kairo-spring-boot-starter-mcp
│   ├── kairo-spring-boot-starter-multi-agent
│   ├── kairo-spring-boot-starter-evolution
│   ├── kairo-spring-boot-starter-expert-team
│   ├── kairo-spring-boot-starter-event-stream
│   ├── kairo-spring-boot-starter-channel-dingtalk
│   ├── kairo-spring-boot-starter-observability
│   ├── kairo-spring-boot-starter-gateway
│   ├── kairo-spring-boot-starter-plugin
│   ├── kairo-spring-boot-starter-cron
│   ├── kairo-spring-boot-starter-lsp
│   └── kairo-spring-boot-starter-acp
│
└── kairo-examples                  — example applications
```

Each cohort aggregator carries zero `<dependencies>` and never appears on a runtime classpath; every leaf still inherits `kairo-parent` directly.

## Module Descriptions

### kairo-api

The SPI interface layer with **zero implementation dependencies**. Defines all extension points: `ModelProvider`, `Tool`, `MemoryStore`, `Skill`, `Hook`, and more. Depend on this module to write extensions without pulling in the full runtime.

### kairo-core

The core runtime engine. Includes:
- **ReAct Engine** — `DefaultReActAgent` implementing the Thought→Action→Observation loop
- **6-Stage Context Compaction** — Progressive pipeline (Snip → Micro → Collapse → Auto → Partial → CircuitBreaker)
- **Model Providers** — Native Anthropic integration + OpenAI-compatible adapter

### kairo-tools

56 built-in tools organized by category:
- **File ops** — Read, Write, Edit, Glob, Grep, Tree, Diff, BatchRead, BatchWrite, SearchReplace, PatchApply, JsonQuery, TemplateRender
- **Execution** — Bash, Monitor, Mvn, Sleep, VerifyExecution
- **Web** — WebFetch, WebSearch, Http, OpenApiHttp
- **Git** — Git, Github
- **Interaction** — AskUser
- **Skills** — SkillList, SkillLoad, SkillManage
- **Agent ops** — AgentSpawn, SendMessage, TeamCreate, TeamDelete, Task*, Todo*, Workflow
- **Plan mode** — EnterPlanMode, ExitPlanMode, ListPlans
- **Memory** — Memory*, TeamMemory*
- **Cron** — Cron*
- **Code intelligence** — Lsp

### kairo-mcp

MCP (Model Context Protocol) integration via StreamableHTTP + Elicitation Protocol, enabling connectivity to external tool servers.

### kairo-multi-agent

Multi-agent orchestration layer:
- **A2A Protocol** — Google ADK-compatible Agent-to-Agent communication
- **TeamCoordinator SPI** — Pluggable team-orchestration contract (ADR-016); default implementation is the expert-team coordinator (plan → generate → evaluate)
- **MessageBus** — In-process pub/sub messaging

### kairo-observability

OpenTelemetry tracing integration centered on distributed tracing (span tree + attributes/events). Currently provides `OTelTracer`, `OTelSpan`, and `GenAiSemanticAttributes` for GenAI-standard span instrumentation. Metrics collection and dashboards are planned for v0.7.

### kairo-spring-boot-starter-* (per-feature)

Spring Boot auto-configuration is split into thirteen per-feature starters under `kairo-starters/`. Start with `kairo-spring-boot-starter-core`; add `-mcp`, `-multi-agent`, `-evolution`, `-expert-team`, `-event-stream`, `-channel-dingtalk`, `-observability`, `-gateway`, `-plugin`, `-cron`, `-lsp`, or `-acp` as needed.

### kairo-plugin

Plugin system compatible with the Claude Code plugin file format (`plugin.json` / `SKILL.md` / `hooks.json` / `.mcp.json`). Supports 5 install sources: LocalPath, GitHub, GitUrl, GitSubdir, and Npm.

### kairo-cron

Scheduled task scheduler with flexible scheduling support for recurring agent operations.

### kairo-gateway

Multi-channel routing, session management, streaming, and mirroring via the `Gateway` SPI. Sits above individual `Channel` adapters.

### kairo-lsp

LSP diagnostics subsystem. Provides `snapshotBaseline` + `notifyChange` + `diagnosticsSince` so tool implementations can report whether an edit introduced new errors (post-edit baseline diff).

### kairo-acp

Agent Client Protocol server handler. Editors (e.g. Zed) drive a Kairo agent as a subprocess via JSON-RPC over stdio. MVP operations: `initialize`, `session.new`, `session.prompt`.
