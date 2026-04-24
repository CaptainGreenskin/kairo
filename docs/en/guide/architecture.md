# Architecture

## Module Overview

26 leaf modules grouped under three reactor-only aggregators (`kairo-capabilities` / `kairo-transports` / `kairo-starters`). Foundation modules stay flat at the top.

```
kairo-parent
├── kairo-bom                       — BOM for dependency version management
├── kairo-api                       — SPI interface layer (zero implementation deps)
├── kairo-core                      — Core runtime (ReAct, compaction, providers)
│
├── kairo-capabilities/             — vertical capability cohort (8 modules)
│   ├── kairo-tools                 — built-in tool suite
│   ├── kairo-mcp                   — MCP protocol integration
│   ├── kairo-multi-agent           — A2A protocol + team coordination
│   ├── kairo-skill                 — Markdown skill registry & loader
│   ├── kairo-evolution             — self-evolution pipeline + governance
│   ├── kairo-expert-team           — plan/generate/evaluate coordinator
│   ├── kairo-observability         — OpenTelemetry exporter
│   └── kairo-security-pii          — PII redaction + JDBC audit + compliance
│
├── kairo-transports/               — I/O boundary cohort (5 modules)
│   ├── kairo-event-stream          — KairoEventBus filtering + backpressure
│   ├── kairo-event-stream-sse      — SSE transport
│   ├── kairo-event-stream-ws       — WebSocket transport
│   ├── kairo-channel               — Channel SPI + LoopbackChannel + TCK
│   └── kairo-channel-dingtalk      — DingTalk webhook + signature verifier
│
├── kairo-starters/                 — Spring Boot starter cohort (9 modules)
│   ├── kairo-spring-boot-starter-core
│   ├── kairo-spring-boot-starter-mcp
│   ├── kairo-spring-boot-starter-multi-agent
│   ├── kairo-spring-boot-starter-evolution
│   ├── kairo-spring-boot-starter-expert-team
│   ├── kairo-spring-boot-starter-event-stream
│   ├── kairo-spring-boot-starter-channel
│   ├── kairo-spring-boot-starter-channel-dingtalk
│   └── kairo-spring-boot-starter-observability
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

17 built-in tools organized by category:
- **File ops** — Read, Write, Edit, Glob, Grep
- **Execution** — Bash, Monitor
- **Interaction** — AskUser
- **Skills** — SkillList, SkillLoad, SkillManage
- **Agent ops** — Spawn, Message, Team, Plan

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

Spring Boot auto-configuration is split into nine per-feature starters under `kairo-starters/`. Start with `kairo-spring-boot-starter-core`; add `-mcp`, `-multi-agent`, `-evolution`, `-expert-team`, `-event-stream`, `-channel`, `-channel-dingtalk`, or `-observability` as needed.
