<h1 align="center">Kairo</h1>

<p align="center"><img src="docs/logo.png" alt="Kairo Logo" width="200" /></p>

<h3 align="center">A Java Agent OS — Runtime Infrastructure for AI Agents</h3>

<p align="center">
  <a href="./README_zh.md">中文</a>
</p>

<p align="center">
  <a href="https://github.com/CaptainGreenskin/kairo/actions"><img src="https://github.com/CaptainGreenskin/kairo/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License" />
  <img src="https://img.shields.io/badge/JDK-17%2B-orange" alt="JDK 17+" />
  <img src="https://img.shields.io/badge/reactive-Project%20Reactor-green" alt="Reactor" />
</p>

---

## Overview

**Kairo** (from Greek *Kairos* — the decisive moment for action) is a Java Agent operating system that provides a complete runtime environment for AI agents. Rather than being yet another LLM wrapper, Kairo models the agent runtime as an operating system, mapping every component to a familiar OS concept:

Kairo is not a wrapper — it's infrastructure. Think Netty for networking, Jackson for serialization, Kairo for AI Agents.

| OS Concept | Kairo Mapping | Description | Status |
|------------|---------------|-------------|--------|
| Memory | Context | Context window as bounded memory with intelligent compaction | Implemented |
| System Call | Tool | 21+ specialized tools — the agent's interface to the outside world | Implemented |
| Process | Agent | Independent execution unit driven by a ReAct loop | Implemented |
| File System | Memory | Persistent knowledge storage (file / in-memory / JDBC) | Implemented |
| Signal | Hook | 10 hook points with CONTINUE/MODIFY/SKIP/ABORT/INJECT decisions | Implemented |
| Executable | Skill | Plug-and-play capability packs in Markdown format | Implemented |
| Job Scheduling | Team | Multi-agent team orchestration via `TeamCoordinator` SPI | Implemented |
| IPC | A2A Protocol | Agent-to-Agent communication for cross-agent invocation | Implemented |
| Middleware | Middleware Pipeline | Declarative request/response interception | Implemented |
| Checkpoint | Snapshot | Agent state serialization and restoration | Implemented |

Kairo is built on Project Reactor for fully reactive, non-blocking execution and supports Claude, GLM, Qwen, GPT, and other models out of the box. The framework is model-agnostic — swap providers without changing agent logic.

## Architecture

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
└── kairo-examples                  — Quick-Start, Skill, Multi-Agent, Channel, Observability demos
```

The aggregators carry zero `<dependencies>` and never appear on a runtime classpath; every leaf still inherits `kairo-parent` directly. Use `mvn -f kairo-<group>/pom.xml test` to build a cohort in one shot.

## Key Features

- **ReAct Engine** — `DefaultReActAgent` implements the full Reasoning-Acting cycle with configurable iteration limits, streaming responses, and multi-layer error recovery
- **6-Stage Context Compaction** — Progressive pipeline (Snip → Micro → Collapse → Auto → Partial → CircuitBreaker) with "Facts First" strategy to preserve raw context as long as possible
- **17 Built-in Tools** — File ops (Read/Write/Edit/Glob/Grep), execution (Bash/Monitor), interaction (AskUser), skills (SkillList/SkillLoad/SkillManage), and agent ops (Spawn/Message/Team/Plan)
- **Read/Write Partition** — READ_ONLY tools execute in parallel, WRITE/SYSTEM_CHANGE tools serialize automatically
- **Human-in-the-Loop** — Three-state permission model (ALLOWED/ASK/DENIED) with `PermissionGuard`
- **Multi-Agent Orchestration** — `TeamCoordinator` SPI with expert-team (plan → generate → evaluate) default, plus in-process MessageBus
- **A2A Protocol** — Agent-to-Agent communication standard (Google ADK-compatible), in-process discovery + invocation, team auto-registration
- **Middleware Pipeline** — Declarative request/response interception with `@MiddlewareOrder` for cross-cutting concerns (logging, auth, rate-limiting)
- **Agent Snapshot/Checkpoint** — Serialize agent state mid-conversation, restore from checkpoint with `AgentBuilder.restoreFrom(snapshot)`
- **Structured Output** — Call models returning typed POJOs with automatic self-correction on format errors
- **Hook Lifecycle** — 10 hook points (Pre/Post Reasoning, Acting, etc.) with CONTINUE/MODIFY/SKIP/ABORT/INJECT decisions
- **Circuit Breaker** — Three-state circuit breaker for both model and tool calls with configurable thresholds
- **Loop Detection** — Hash-based + frequency-based dual detection to prevent infinite agent loops
- **Cooperative Cancellation** — Graceful agent termination with state preservation
- **MCP Integration** — StreamableHTTP + Elicitation Protocol for external tool server connectivity
- **Skill System** — Markdown-based skill definitions with `TriggerGuard` anti-contamination design
- **Plan Mode** — Separate planning from execution; write tools blocked during planning
- **Model Harness** — Deep Anthropic integration + OpenAI-compatible fallback for GLM, Qwen, GPT, etc.
- **Session Persistence** — File-based state serialization with TTL cleanup

## Quick Start

**Requirements:** JDK 17+, Maven 3.8+

### 1. Add Dependency

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.captaingreenskin</groupId>
            <artifactId>kairo-bom</artifactId>
            <version>1.0.0</version>
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

If you already have a Spring-managed `Agent` bean, your "Hello World" can be just 3 lines:

```java
@Autowired Agent agent;
Msg reply = agent.call(MsgBuilder.user("Hello, Kairo!")).block();
System.out.println(reply.getTextContent());
```

### 3. Spring Boot Integration

Add the starter dependency and configure via `application.yml`:

```xml
<dependency>
    <groupId>io.github.captaingreenskin</groupId>
    <artifactId>kairo-spring-boot-starter-core</artifactId>
</dependency>
```

Add per-feature starters as needed: `kairo-spring-boot-starter-mcp`, `-multi-agent`, `-evolution`, `-expert-team`, `-event-stream`, `-channel`, `-channel-dingtalk`, `-observability`.

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
@Autowired
Agent agent;

@PostMapping("/chat")
public String chat(@RequestBody String message) {
    return agent.call(MsgBuilder.user(message)).block().getTextContent();
}
```

That's it — a few lines of YAML and the agent is ready.

## Model Support

| Provider | Models | API Type | Environment Variable | Status |
|----------|--------|----------|---------------------|--------|
| **Anthropic** | Claude Sonnet, Claude Opus, Claude Haiku | Native Anthropic API | `ANTHROPIC_API_KEY` | Implemented |
| **Zhipu AI** | GLM-4-Plus, GLM-4 | OpenAI-compatible | `GLM_API_KEY` | Implemented |
| **DashScope** | Qwen-Plus, Qwen-Max, Qwen-Turbo | OpenAI-compatible | `QWEN_API_KEY` | Implemented |
| **OpenAI** | GPT-4o, GPT-4, GPT-3.5 | OpenAI-compatible | `OPENAI_API_KEY` | Implemented |

```java
// Anthropic (native API)
AnthropicProvider claude = new AnthropicProvider(apiKey);

// GLM / Qwen / GPT (OpenAI-compatible)
OpenAIProvider provider = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
```

## Build

```bash
# Build and install all modules (required before running demos)
mvn clean install

# Run tests only (2,551 tests across the reactor as of v1.0.0 GA)
mvn test
```

### Running the Demos

```bash
# Mock mode (no API key needed)
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--mock"

# GLM mode (requires GLM_API_KEY)
export GLM_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--glm"

# Qwen mode (requires QWEN_API_KEY)
export QWEN_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--qwen"

# Anthropic mode (requires ANTHROPIC_API_KEY)
export ANTHROPIC_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample"
```

More demos available:

| Demo | API Key | What it tests |
|------|---------|---------------|
| `AgentExample --mock` | No | Basic ReAct loop with mock model |
| `AgentExample --glm` | GLM | ReAct loop with GLM-4-Plus |
| `AgentExample --qwen` | Qwen | ReAct loop with Qwen-Plus |
| `FullToolsetExample` | Qwen | All 6 tools: read, write, edit, glob, grep, bash |
| `SkillExample` | Qwen | Skill system: list, load, and use Markdown skills |
| `MultiAgentExample` | No | TeamCoordinator dispatch + in-process MessageBus pub/sub |
| `SessionExample` | No | FileMemoryStore + SessionSerializer round-trip |
| Spring Boot Demo | Yes | REST API, streaming, structured output, hooks, MCP |

## Roadmap

| Version | Theme | Status |
|---------|-------|--------|
| v0.1–v0.4 | Core Runtime + SPI + A2A + Middleware + Snapshot | Released |
| v0.5 | Agents That Remember — Memory SPI + Embedding + Checkpoint/Rollback | Released |
| v0.6 | Exception Phase B + Interrupt/Resume + Team Patterns | Released |
| v0.7 | Guardrail SPI + Security Observability + MCP Default DENY_SAFE | Released |
| v0.8 | DurableExecutionStore + ResourceConstraint + Cost-Aware Routing | Released |
| v0.9.0 | Channel SPI + KairoEventBus + OTel Exporter | Released |
| v0.9.1 | DingTalk Channel Adapter (first concrete `Channel` transport) | Released |
| v0.10.0 | Core Refactor Waves — hooks, deprecations, SPI scaffolding | Released |
| v0.10.1 | Expert Team Orchestration MVP | Released |
| v0.10.2 | Structural Debt — kairo-skill split, ProviderPipeline, MCP capability record | Released |
| v1.0.0-RC1 | SPI Stabilization — 119 `@Stable` / 78 `@Experimental`, japicmp gate, 77.4% core coverage | Released |
| v1.0.0-RC2 | API Reference docs, bilingual parity, observability + channel examples | Released |
| v1.0.0 GA | Enterprise Security (PII + Audit + Compliance), reactor restructure | Released |

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

Look for issues labeled [`good first issue`](https://github.com/CaptainGreenskin/kairo/labels/good%20first%20issue) to get started, or [`help wanted`](https://github.com/CaptainGreenskin/kairo/labels/help%20wanted) for more challenging tasks.

## Community

- [GitHub Discussions](https://github.com/CaptainGreenskin/kairo/discussions) — Questions, ideas, and general discussion
- [GitHub Issues](https://github.com/CaptainGreenskin/kairo/issues) — Bug reports and feature requests

<!-- TODO: Add community channels when available -->
<!-- - [Discord](https://discord.gg/xxx) -->
<!-- - WeChat / DingTalk group QR codes -->

## License

Kairo is licensed under the [Apache License 2.0](./LICENSE).

```
Copyright 2025-2026 the Kairo authors.
```
