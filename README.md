<h1 align="center">Kairo</h1>

<p align="center"><img src="docs/logo.png" alt="Kairo Logo" width="200" /></p>

<h3 align="center">Java Agent OS — Runtime Infrastructure for AI Agents</h3>

<p align="center">
  <a href="./README_zh.md">中文</a> ·
  <a href="https://github.com/CaptainGreenskin/kairo/issues">Issues</a> ·
  <a href="https://github.com/CaptainGreenskin/kairo/discussions">Discussions</a>
</p>

<p align="center">
  <a href="https://github.com/CaptainGreenskin/kairo/actions"><img src="https://github.com/CaptainGreenskin/kairo/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="https://central.sonatype.com/namespace/io.github.captaingreenskin"><img src="https://img.shields.io/maven-central/v/io.github.captaingreenskin/kairo-core?label=Maven%20Central" alt="Maven Central" /></a>
  <img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License" />
  <img src="https://img.shields.io/badge/JDK-17%2B-orange" alt="JDK 17+" />
  <img src="https://img.shields.io/badge/reactive-Project%20Reactor-green" alt="Reactor" />
</p>

---

## What is Kairo?

**Kairo** (from Greek *Kairos* — the decisive moment for action) is a Java Agent operating system: a complete runtime environment for building, running, and managing AI agents in Java.

Kairo is not an LLM wrapper. It's **infrastructure** — the 98.4% that keeps an agent alive in production. Think Netty for networking, Jackson for serialization, **Kairo for AI Agents**.

### The Core Insight

```
10-step agent, 90% reliability per step → 34.9% end-to-end success
20-step agent, 90% reliability per step →  3.9% end-to-end success
```

Multi-step agent failures are a math problem, not an intelligence problem. LLMs will get smarter, but state management, error recovery, cost control, security guardrails, and context compaction will always need framework-level infrastructure. That's what Kairo provides.

### OS Concept Mapping

Every component in Kairo maps to a familiar operating system concept:

| OS Concept | Kairo Mapping | Description |
|------------|---------------|-------------|
| Process | Agent | Independent execution unit driven by a ReAct loop |
| Memory | Context | Context window as bounded memory with 6-stage intelligent compaction |
| File System | Memory | Persistent knowledge storage (file / in-memory / JDBC) |
| System Call | Tool | 56 built-in tools — the agent's interface to the outside world |
| Signal | Hook | 10 hook points with CONTINUE / MODIFY / SKIP / ABORT / INJECT decisions |
| Executable | Skill | Plug-and-play capability packs in Markdown format |
| IPC | A2A Protocol | Agent-to-Agent communication (Google ADK-compatible) |
| Job Scheduling | Cron + Team | Scheduled tasks + multi-agent team orchestration |
| Middleware | Middleware Pipeline | Declarative request/response interception |
| Checkpoint | Snapshot | Agent state serialization and restoration |
| Package Manager | Plugin | Install/enable/disable skills, hooks, MCP servers, agents |
| Network | Gateway | Multi-channel routing, session management, streaming, mirroring |
| Device Driver | Channel | IM adapter layer (DingTalk shipped, Slack/Feishu/Telegram planned) |

---

## Kairo Code — The Dogfooding Application

**Kairo Code** is a coding agent CLI built entirely on the Kairo framework — our own dogfood to prove the framework's real-world value. It demonstrates how Kairo's SPI-driven architecture can power a production-grade AI coding assistant with:

- Interactive REPL with tool approval, streaming, and session persistence
- 56 tools (file ops, git, bash, web search, MCP, task management, and more)
- Skill system for extensible, markdown-defined capabilities
- Multi-agent team coordination for complex tasks
- Bridge SPI for IDE integration (VS Code, JetBrains, Zed)

Kairo Code lives in a [separate repository](https://github.com/CaptainGreenskin/kairo-code) and imports Kairo as a dependency — zero framework code is duplicated.

---

## Architecture

31 Maven modules organized into 3 cohort aggregators plus foundation modules:

```
kairo-parent (root)
├── kairo-bom                           — BOM for dependency management
├── kairo-api                           — SPI interface layer (zero deps, @Stable / @Experimental)
├── kairo-core                          — Core runtime (ReAct, compaction, providers, resilience)
│
├── kairo-capabilities/                 — 12 capability modules
│   ├── kairo-tools                     — 56 built-in tools
│   ├── kairo-mcp                       — MCP protocol (StreamableHTTP + Elicitation)
│   ├── kairo-multi-agent               — A2A protocol + team coordination + MoA
│   ├── kairo-skill                     — Markdown skill registry & loader
│   ├── kairo-evolution                 — Agent self-evolution pipeline + governance
│   ├── kairo-expert-team               — Plan → Generate → Evaluate coordinator
│   ├── kairo-observability             — OpenTelemetry event exporter
│   ├── kairo-security-pii              — PII redaction + JDBC audit + compliance
│   ├── kairo-plugin                    — Plugin system (Claude Code format compatible)
│   ├── kairo-cron                      — Scheduled task scheduler
│   ├── kairo-gateway                   — Multi-channel routing / session / streaming / mirror
│   ├── kairo-lsp                       — LSP diagnostics (post-edit baseline diff)
│   └── kairo-acp                       — Agent Client Protocol (editor integration)
│
├── kairo-transports/                   — 4 I/O boundary modules
│   ├── kairo-event-stream              — Event bus (transport-agnostic, zero Spring deps)
│   ├── kairo-event-stream-sse          — SSE transport (WebFlux)
│   ├── kairo-event-stream-ws           — WebSocket transport (WebFlux) + Bridge SPI
│   └── kairo-channel-dingtalk          — DingTalk Channel adapter
│
├── kairo-starters/                     — 13 Spring Boot starters
│   └── kairo-spring-boot-starter-*     — One starter per capability (idiomatic Spring Boot)
│
└── kairo-examples/                     — Quick-start, Skill, Multi-Agent, Channel, Observability demos
```

Aggregators carry zero `<dependencies>` and never appear on a runtime classpath. Every leaf module inherits `kairo-parent` directly. Build a cohort in one shot: `mvn -pl kairo-capabilities -am test`.

---

## Key Features

### Agent Runtime

- **ReAct Engine** — Full Reasoning-Acting cycle with configurable iteration limits, streaming, and multi-layer error recovery (prompt_too_long / rate_limited / server_error / max_output_tokens)
- **6-Stage Context Compaction** — Progressive pipeline: Snip → Micro → Collapse → Auto → Partial → CircuitBreaker. "Facts First" strategy preserves raw context as long as possible
- **Cooperative Cancellation** — Graceful agent termination with state preservation
- **Agent Snapshot / Checkpoint** — Serialize agent state mid-conversation, restore with `AgentBuilder.restoreFrom(snapshot)`
- **Session Persistence** — File-based state serialization with TTL cleanup via `SessionStorageProvider` SPI

### Tools & Execution

- **56 Built-in Tools** — File ops (Read / Write / Edit / Glob / Grep / Tree / Diff / BatchRead / BatchWrite / SearchReplace / PatchApply / JsonQuery / TemplateRender), execution (Bash / Monitor / Mvn / Sleep / VerifyExecution), web (WebFetch / WebSearch / Http / OpenApiHttp), git (Git / Github), interaction (AskUser), skills (SkillList / SkillLoad / SkillManage), agent ops (AgentSpawn / SendMessage / TeamCreate / TeamDelete / TaskCreate / TaskGet / TaskList / TaskUpdate / TodoRead / TodoWrite / Workflow), plan mode (EnterPlanMode / ExitPlanMode / ListPlans), memory (MemoryRead / MemoryWrite / MemoryDelete / TeamMemoryRead / TeamMemoryWrite / TeamMemoryDelete), cron (CronCreate / CronDelete / CronEdit / CronList / CronPause / CronResume / CronTrigger), and code intelligence (Lsp)
- **Read/Write Partition** — `READ_ONLY` tools execute in parallel; `WRITE` / `SYSTEM_CHANGE` tools serialize automatically
- **Human-in-the-Loop** — Three-state permission model (ALLOWED / ASK / DENIED) with `PermissionGuard`
- **Execution Sandbox** — `ExecutionSandbox` SPI with `LocalProcessSandbox` default; Docker/K8s pluggable

### Intelligence & Learning

- **Skill System** — Markdown-based skill definitions with `TriggerGuard` anti-contamination design
- **Agent Self-Evolution** — Quarantine → Scan → Activate governance pipeline for learned skills
- **Plan Mode** — Separate planning from execution; write tools blocked during planning
- **Structured Output** — Model calls returning typed POJOs with automatic self-correction on format errors

### Multi-Agent

- **Team Orchestration** — `TeamCoordinator` SPI with expert-team (Plan → Generate → Evaluate) default
- **A2A Protocol** — Google ADK-compatible Agent-to-Agent communication with in-process discovery
- **Mixture of Agents (MoA)** — Multiple model perspectives aggregated for higher quality output
- **In-Process MessageBus** — Pub/sub for agent-to-agent communication within a JVM

### Resilience & Safety

- **Circuit Breaker** — Three-state circuit breaker for both model and tool calls with configurable thresholds
- **Loop Detection** — Hash-based + frequency-based dual detection to prevent infinite agent loops
- **Guardrail SPI** — 4-phase input/output validation with `GuardrailChain`
- **PII Redaction** — Pattern-based redaction (EMAIL / PHONE / CREDIT_CARD / SSN / API_KEY / JWT)
- **JDBC Audit Trail** — Append-only audit log with Flyway schema; per-run compliance reports
- **Cost-Aware Routing** — `ModelCostRouter` with tier-based fallback chains

### Integration & Connectivity

- **MCP Integration** — StreamableHTTP + Elicitation Protocol for external tool servers
- **Plugin System** — Claude Code format compatible; 5 install sources (LocalPath / GitHub / GitUrl / GitSubdir / Npm)
- **Gateway** — Multi-channel routing, session management, streaming, mirroring across IM adapters
- **DingTalk Channel** — Webhook signature verification, message mapping, outbound client, dedup
- **Bridge SPI** — WebSocket-based IDE integration (`agent.run` / `agent.cancel` / `tool.approve` / ...)
- **ACP (Agent Client Protocol)** — JSON-RPC over stdio for editors like Zed to drive Kairo agents
- **LSP Diagnostics** — Post-edit baseline diff for "did this edit introduce new errors?"
- **OpenTelemetry** — `KairoEventOTelExporter` bridges events to OTel logs with domain filter, sampling, key redaction

### Infrastructure

- **Hook Lifecycle** — 10 hook points: PreReasoning → PostReasoning → PreActing → PostActing → PreToolExecute → PostToolExecute → PreModelCall → PostModelCall → OnLoopDetected → OnError. Decisions: CONTINUE / MODIFY / SKIP / ABORT / INJECT
- **Middleware Pipeline** — Declarative request/response interception with `@MiddlewareOrder`
- **Tenant Context** — `TenantContextHolder` with Reactor context propagation for multi-tenant deployments
- **Workspace Provider** — `WorkspaceProvider` SPI with path-traversal defense; relative path resolution for all file tools

---

## Quick Start

**Requirements:** JDK 17+, Maven 3.8+

### 1. Add Dependency

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.captaingreenskin</groupId>
            <artifactId>kairo-bom</artifactId>
            <version>0.8.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.captaingreenskin</groupId>
        <artifactId>kairo-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.captaingreenskin</groupId>
        <artifactId>kairo-tools</artifactId>
    </dependency>
</dependencies>
```

### 2. Write Your First Agent

```java
// 1. Register tools
DefaultToolRegistry registry = new DefaultToolRegistry();
registry.registerTool(BashTool.class);
registry.registerTool(WriteTool.class);
registry.registerTool(ReadTool.class);

// 2. Create tool executor with permission guard
DefaultPermissionGuard guard = new DefaultPermissionGuard();
DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

// 3. Choose a model provider
AnthropicProvider provider = new AnthropicProvider(System.getenv("ANTHROPIC_API_KEY"));

// 4. Build the agent
Agent agent = AgentBuilder.create()
    .name("coding-agent")
    .model(provider)
    .modelName("claude-sonnet-4-20250514")
    .tools(registry)
    .toolExecutor(executor)
    .systemPrompt("You are a helpful coding assistant.")
    .maxIterations(20)
    .streaming(true)
    .build();

// 5. Run
Msg result = agent.call(MsgBuilder.user("Create a HelloWorld.java, compile and run it.")).block();
```

Already using Spring Boot? Three lines is all you need:

```java
@Autowired Agent agent;
Msg reply = agent.call(MsgBuilder.user("Hello, Kairo!")).block();
System.out.println(reply.getTextContent());
```

### 3. Spring Boot Integration

```xml
<dependency>
    <groupId>io.github.captaingreenskin</groupId>
    <artifactId>kairo-spring-boot-starter-core</artifactId>
</dependency>
```

```yaml
kairo:
  model:
    provider: anthropic
    api-key: ${ANTHROPIC_API_KEY}
  tool:
    enable-file-tools: true
    enable-exec-tools: true
```

```java
@PostMapping("/chat")
public String chat(@RequestBody String message) {
    return agent.call(MsgBuilder.user(message)).block().getTextContent();
}
```

Add per-feature starters as needed:

| Starter | Capability |
|---------|------------|
| `kairo-spring-boot-starter-mcp` | MCP protocol integration |
| `kairo-spring-boot-starter-multi-agent` | A2A + team coordination |
| `kairo-spring-boot-starter-evolution` | Agent self-evolution |
| `kairo-spring-boot-starter-expert-team` | Plan/Generate/Evaluate loop |
| `kairo-spring-boot-starter-event-stream` | SSE + WebSocket event bus |
| `kairo-spring-boot-starter-gateway` | Multi-channel routing |
| `kairo-spring-boot-starter-channel-dingtalk` | DingTalk adapter |
| `kairo-spring-boot-starter-observability` | OpenTelemetry exporter |
| `kairo-spring-boot-starter-plugin` | Plugin system |
| `kairo-spring-boot-starter-cron` | Scheduled tasks |
| `kairo-spring-boot-starter-lsp` | LSP diagnostics |
| `kairo-spring-boot-starter-acp` | Agent Client Protocol |

---

## Model Support

Kairo is model-agnostic. Swap providers without changing agent logic.

| Provider | Models | API Type | Env Variable | Status |
|----------|--------|----------|-------------|--------|
| **Anthropic** | Claude Opus, Sonnet, Haiku | Native Anthropic API | `ANTHROPIC_API_KEY` | Native integration |
| **Google** | Gemini Pro, Flash | Native Gemini API | `GEMINI_API_KEY` | Native integration |
| **Zhipu AI** | GLM-4-Plus, GLM-4 | OpenAI-compatible | `GLM_API_KEY` | Via OpenAI adapter |
| **DashScope** | Qwen-Plus, Qwen-Max, Qwen-Turbo | OpenAI-compatible | `QWEN_API_KEY` | Via OpenAI adapter |
| **OpenAI** | GPT-4o, GPT-4, GPT-3.5 | OpenAI-compatible | `OPENAI_API_KEY` | Via OpenAI adapter |

```java
// Anthropic (native API — deep integration: extended thinking, prompt caching, cache-break detection)
AnthropicProvider claude = new AnthropicProvider(apiKey);

// Gemini (native API)
GeminiProvider gemini = new GeminiProvider(apiKey);

// GLM / Qwen / GPT / any OpenAI-compatible (uniform adapter)
OpenAIProvider provider = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
```

---

## SPI Overview

Kairo's plugin architecture is SPI-driven. All extension points live in `kairo-api` with stability annotations:

| SPI | Package | Description |
|-----|---------|-------------|
| `Agent` | `io.kairo.api.agent` | Core execution interface |
| `ModelProvider` | `io.kairo.api.model` | Model inference with streaming |
| `ToolExecutor` | `io.kairo.api.tool` | Tool execution framework |
| `ContextManager` | `io.kairo.api.context` | Context lifecycle management |
| `MemoryStore` | `io.kairo.api.memory` | Persistent knowledge storage |
| `Middleware` | `io.kairo.api.middleware` | Request/response interception |
| `TeamCoordinator` | `io.kairo.api.team` | Multi-agent orchestration |
| `ExecutionSandbox` | `io.kairo.api.sandbox` | Sandboxed command execution |
| `WorkspaceProvider` | `io.kairo.api.workspace` | Workspace management with path safety |
| `Gateway` | `io.kairo.api.gateway` | Multi-channel routing layer |
| `Channel` | `io.kairo.api.gateway` | Single IM / webhook adapter |
| `PluginManager` | `io.kairo.api.plugin` | Plugin install / enable / disable |
| `BridgeServer` | `io.kairo.api.bridge` | IDE integration via WebSocket |
| `AcpAgent` | `io.kairo.api.acp` | Agent Client Protocol (stdio JSON-RPC) |
| `LspService` | `io.kairo.api.lsp` | LSP diagnostics subsystem |
| `TenantContextHolder` | `io.kairo.api.tenant` | Multi-tenant context propagation |

v1.1 surface: **119 `@Stable`** types + **78 `@Experimental`** types. Binary compatibility enforced by japicmp.

---

## Documentation

Kairo ships with bilingual VitePress documentation (English + Chinese):

```bash
cd docs && npm install && npm run docs:dev
```

Key pages:

- [Introduction](docs/en/guide/introduction.md) — Philosophy and OS mapping
- [Getting Started](docs/en/guide/getting-started.md) — First agent in 5 minutes
- [Features](docs/en/guide/features.md) — Complete feature catalogue
- [Architecture](docs/en/guide/architecture.md) — Module structure and data flow
- [SPI Governance](docs/en/guide/spi-governance.md) — Stability annotations and compatibility policy
- [Plugin System](docs/en/guide/plugins.md) — Claude Code format compatible plugins
- [API Reference](docs/en/api/) — Agent, ModelProvider, ToolHandler, Msg, KairoException
- [ADR Archive](docs/adr/) — 30 Architecture Decision Records

---

## Build & Test

```bash
# Compile all modules
mvn compile

# Run all tests (~2,500+ tests across 350+ suites)
mvn test

# Run a single module
mvn test -pl kairo-core

# Run a capability cohort
mvn test -pl kairo-capabilities -am

# Integration tests
mvn verify -Pintegration-tests

# Format check (must pass before commit)
mvn spotless:check

# Auto-fix formatting
mvn spotless:apply

# Package (skip tests)
mvn clean package -DskipTests
```

### Running Demos

```bash
# Mock mode (no API key needed)
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--mock"

# Anthropic mode
export ANTHROPIC_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample"

# GLM mode
export GLM_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--glm"
```

| Demo | API Key | What it shows |
|------|---------|---------------|
| `AgentExample --mock` | None | ReAct loop with mock model |
| `AgentExample` | Anthropic | ReAct loop with Claude |
| `AgentExample --glm` | GLM | ReAct loop with GLM-4 |
| `AgentExample --qwen` | Qwen | ReAct loop with Qwen |
| `FullToolsetExample` | Qwen | File read/write/edit, glob, grep, bash |
| `SkillExample` | Qwen | Skill list, load, and use |
| `MultiAgentExample` | None | Team coordination + MessageBus |
| `SessionExample` | None | FileMemoryStore + session serialization |
| `ObservabilityExample` | None | KairoEventBus → OTel exporter |
| `DingTalkBotDemo` | DingTalk | DingTalk Channel webhook |
| `DesktopAgentDemo` | Varies | Desktop agent with full toolset |
| Spring Boot Demo | Yes | REST API, streaming, hooks, MCP, permissions |

---

## Roadmap

| Version | Theme | Status |
|---------|-------|--------|
| v0.1 – v0.4 | Core Runtime + SPI + A2A + Middleware + Snapshot | Released |
| v0.5 | Memory SPI + Embedding + Checkpoint/Rollback | Released |
| v0.6 | Exception Unification + Interrupt/Resume + Team Patterns | Released |
| v0.7 | Guardrail SPI + MCP Security + Cost Routing | Released |
| v0.8 | DurableExecution + ResourceConstraint + Cost-Aware Routing | Released |
| v0.9.0 | Channel SPI + Event Stream (SSE + WS) + OTel Exporter | Released |
| v0.9.1 | DingTalk Channel Adapter | Released |
| v0.10.x | Core Refactor + Expert Team + Structural Debt Cleanup | Released |
| v1.0.0 | **GA** — Enterprise Security (PII + Audit + Compliance), 119 `@Stable` SPIs | Released |
| v1.1.0 | SPI Foundations — Sandbox, Workspace, Tenant, Bridge | Released |
| v1.1.1 | ConsoleApprovalHandler safety + Kairo Code rebrand | Released |
| v1.2 | Plugin SPI + Gateway + Cron + LSP + ACP | In Progress |
| v1.3+ | Remote Workspace, Ephemeral Sandbox, distributed multi-agent | Planned |

Published to Maven Central: `io.github.captaingreenskin:kairo-*`

---

## Project Stats

| Metric | Value |
|--------|-------|
| Maven modules | 31 |
| Source files | 950+ Java files |
| Test files | 580+ test classes |
| Test count | 2,500+ (Surefire) |
| SPI types | 197 (119 `@Stable` + 78 `@Experimental`) |
| Built-in tools | 56 |
| ADRs | 30 |
| Starters | 13 |

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 17+ (`-release 17`) |
| Spring Boot | 3.3.0 |
| Project Reactor | 3.7.3 |
| JUnit 5 | 5.11.4 |
| Jackson | 2.18.2 |
| MCP SDK | 1.1.1 |
| OpenTelemetry | 1.44.1 |
| Mockito | 5.11.0 |

All versions managed by `kairo-bom` — never declare versions in child modules.

---

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

- [`good first issue`](https://github.com/CaptainGreenskin/kairo/labels/good%20first%20issue) — Great for getting started
- [`help wanted`](https://github.com/CaptainGreenskin/kairo/labels/help%20wanted) — More challenging tasks

### Development Workflow

1. Branch from `main`: `git checkout -b feature/issue-{number}`
2. Follow existing package structure
3. Run `mvn spotless:apply` before commit
4. Run `mvn test -pl <module>` to verify
5. Commit: `feat(kairo-core): short description`
6. Create PR, link issue

---

## Community

- [GitHub Discussions](https://github.com/CaptainGreenskin/kairo/discussions) — Questions, ideas, general discussion
- [GitHub Issues](https://github.com/CaptainGreenskin/kairo/issues) — Bug reports and feature requests

---

## License

Kairo is licensed under the [Apache License 2.0](./LICENSE).

```
Copyright 2025-2026 the Kairo authors.
```
