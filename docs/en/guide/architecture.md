# Architecture

## Module Overview

```
kairo-parent
├── kairo-bom                  — BOM for dependency version management
├── kairo-api                  — SPI interface layer (zero implementation dependencies)
├── kairo-core                 — Core runtime (ReAct engine, compaction, model providers)
├── kairo-tools                — Built-in tool suite (21 tools)
├── kairo-mcp                  — MCP protocol integration (StreamableHTTP)
├── kairo-multi-agent          — Multi-agent orchestration (A2A Protocol, Team, TaskBoard)
├── kairo-observability        — OpenTelemetry tracing integration (spans + attributes)
├── kairo-spring-boot-starter  — Spring Boot auto-configuration
└── kairo-examples             — Example applications
```

## Module Descriptions

### kairo-api

The SPI interface layer with **zero implementation dependencies**. Defines all extension points: `ModelProvider`, `Tool`, `MemoryStore`, `Skill`, `Hook`, and more. Depend on this module to write extensions without pulling in the full runtime.

### kairo-core

The core runtime engine. Includes:
- **ReAct Engine** — `DefaultReActAgent` implementing the Thought→Action→Observation loop
- **6-Stage Context Compaction** — Progressive pipeline (Snip → Micro → Collapse → Auto → Partial → CircuitBreaker)
- **Model Providers** — Native Anthropic integration + OpenAI-compatible adapter

### kairo-tools

21 built-in tools organized by category:
- **File ops** — Read, Write, Edit, Glob, Grep
- **Execution** — Bash, Monitor
- **Interaction** — AskUser
- **Skills** — SkillList, SkillLoad
- **Agent ops** — Spawn, Message, Task, Team, Plan

### kairo-mcp

MCP (Model Context Protocol) integration via StreamableHTTP + Elicitation Protocol, enabling connectivity to external tool servers.

### kairo-multi-agent

Multi-agent orchestration layer:
- **A2A Protocol** — Google ADK-compatible Agent-to-Agent communication
- **TeamCoordinator SPI** — Pluggable team-orchestration contract (ADR-016); default implementation is the expert-team coordinator (plan → generate → evaluate)
- **MessageBus** — In-process pub/sub messaging

### kairo-observability

OpenTelemetry tracing integration centered on distributed tracing (span tree + attributes/events). Currently provides `OTelTracer`, `OTelSpan`, and `GenAiSemanticAttributes` for GenAI-standard span instrumentation. Metrics collection and dashboards are planned for v0.7.

### kairo-spring-boot-starter

Spring Boot auto-configuration. Add the starter and configure via `application.yml` — agent is ready with minimal code.
