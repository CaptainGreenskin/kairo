<h1 align="center">Kairo</h1>

<h3 align="center">A Java Agent OS -- Runtime Infrastructure for AI Agents</h3>

<p align="center">
  <img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License" />
  <img src="https://img.shields.io/badge/JDK-17%2B-orange" alt="JDK 17+" />
  <img src="https://img.shields.io/badge/tests-717%20passed-brightgreen" alt="Tests" />
  <img src="https://img.shields.io/badge/modules-7-informational" alt="Modules" />
  <img src="https://img.shields.io/badge/version-0.1.0--SNAPSHOT-yellow" alt="Version" />
</p>

---

## Overview

**Kairo** (from Greek *Kairos* -- the decisive moment for action) is a Java Agent operating system that provides a complete runtime environment for AI agents. Rather than being yet another LLM wrapper, Kairo models the agent runtime as an operating system, mapping every component to a familiar OS concept:

| OS Concept | Kairo Mapping | Description |
|------------|---------------|-------------|
| Memory | Context | Context window as bounded memory with intelligent compaction |
| System Call | Tool | 30+ specialized tools -- the agent's interface to the outside world |
| Process | Agent | Independent execution unit driven by a ReAct loop |
| File System | Memory | Persistent knowledge storage (file / in-memory) |
| Signal | Hook | Lifecycle event chain (Pre/Post Reasoning, Acting) |
| Executable | Skill | Plug-and-play capability packs in Markdown format |
| Job Scheduling | Task + Team | Multi-agent task orchestration and team collaboration |

This OS metaphor provides clear separation of concerns and makes agent internals instantly recognizable to any systems programmer. Kairo is built on Project Reactor for fully reactive, non-blocking execution and supports Claude, GLM, Qwen, GPT, and other models out of the box.

## Architecture

```
kairo-parent (0.1.0-SNAPSHOT)
├── kairo-api                  — SPI interface layer
├── kairo-core                 — Core runtime implementation
├── kairo-tools                — Built-in tool suite (20+ tools)
├── kairo-multi-agent          — Multi-agent orchestration
├── kairo-spring-boot-starter  — Spring Boot auto-configuration
└── kairo-demo                 — Demo applications
```

| Module | Description |
|--------|-------------|
| **kairo-api** | Pure SPI interfaces with zero implementation dependencies. Defines Agent, Context, Tool, Hook, Skill, Task, Team, Model, and Message contracts. |
| **kairo-core** | Core runtime: ReAct engine (`DefaultReActAgent`), 6-stage compaction pipeline, Anthropic/OpenAI model providers, hook chain, memory stores, skill system. |
| **kairo-tools** | 20+ built-in tools across file ops (`Read/Write/Edit/Glob/Grep`), execution (`Bash/Monitor`), interaction (`AskUser`), skills (`SkillList/SkillLoad`), and agent ops (`Spawn/Message/Task/Team/Plan`). |
| **kairo-multi-agent** | Task board, plan builder, team manager, team scheduler, and in-process message bus for multi-agent collaboration. |
| **kairo-spring-boot-starter** | `@EnableKairo` annotation and auto-configuration for seamless Spring Boot integration. |
| **kairo-demo** | Ready-to-run demos supporting Mock, GLM (Zhipu), Qwen (DashScope), and Claude (Anthropic) modes. |

## Key Features

### Tool System

- **Read/write partition** -- read-only tools execute in parallel, write tools serialize automatically
- **30+ specialized tools** -- annotation-scanned registration, precise parameter definitions, input validation
- **Streaming execution** -- non-blocking tool execution with reactive backpressure
- **Human-in-the-loop approval** -- `PermissionGuard` controls side-effect authorization per tool

### Agent Loop

- **ReAct engine** -- `DefaultReActAgent` (498 lines) implements the full Reasoning-Acting cycle
- **Multi-layer error recovery** -- 3-retry with exponential backoff, automatic model fallback chain
- **Configurable iteration limits** -- prevent runaway loops with `maxIterations`
- **Streaming responses** -- real-time token streaming with non-streaming fallback

### Context Engineering

- **6-stage compaction pipeline** with progressive thresholds:

  | Stage | Strategy | Trigger | Action |
  |-------|----------|---------|--------|
  | 1 | SnipCompaction | 80% | Trim old tool results |
  | 2 | MicroCompaction | 85% | Clear tool result content |
  | 3 | CollapseCompaction | 90% | Collapse message groups |
  | 4 | AutoCompaction | 95% | LLM-generated summary |
  | 5 | PartialCompaction | 98% | Selective last-resort compression |
  | 6 | CircuitBreaker | -- | Halt after 3 consecutive failures |

- **"Facts First" strategy** -- preserve raw context as long as possible; compaction is a safety net, not a policy
- **Prompt caching** -- reduce redundant LLM calls for repeated context
- **Post-compact recovery** -- restore critical information after aggressive compaction
- **Token budget management** -- configurable per-agent token limits

### Model Harness

- **Deep Anthropic integration** -- `AnthropicProvider` (664 lines) with native Claude API support
- **OpenAI-compatible fallback** -- `OpenAIProvider` supports any OpenAI-compatible endpoint
- **Dynamic thinking budget** -- adjust reasoning depth based on task complexity
- **Tool verbosity adaptation** -- tune tool descriptions per model capability

### Plan Mode

- **Mode isolation** -- separate planning from execution to improve reasoning quality
- **Plan file persistence** -- plans survive agent restarts
- **Approval flow** -- review and approve plans before execution via `EnterPlanModeTool` / `ExitPlanModeTool`

### Multi-Agent Orchestration

- **TaskBoard** -- track task state, dependencies, and assignment
- **PlanBuilder** -- decompose complex tasks into executable sub-task graphs
- **TeamScheduler** -- coordinate concurrent multi-agent execution
- **MessageBus** -- in-process async communication between agents

### Session Persistence

- **File-based persistence** -- serialize agent state to disk for session recovery
- **TTL cleanup** -- automatic expiration of stale sessions
- **Session ID management** -- deterministic session identifiers for reproducibility

### Skill System

- **Markdown-based definitions** -- skills defined as Markdown files, loaded from the filesystem
- **Anti-contamination design** -- `TriggerGuard` (0.8 threshold) prevents false activation
- **Match priority** -- slash command > exact match > keyword match

## Quick Start

**Requirements:** JDK 17+, Maven 3.8+

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
    .tools(registry)
    .toolExecutor(executor)
    .systemPrompt("You are a helpful coding assistant.")
    .maxIterations(20)
    .build();

// 5. Run
Msg result = agent.call(MsgBuilder.user("Create a HelloWorld.java, compile and run it.")).block();
```

## Model Support

Kairo supports multiple model providers through a unified interface:

| Provider | Models | API Type | Environment Variable |
|----------|--------|----------|---------------------|
| **Anthropic** | Claude Sonnet, Claude Opus, Claude Haiku | Native Anthropic API | `ANTHROPIC_API_KEY` |
| **Zhipu AI** | GLM-4-Plus, GLM-4 | OpenAI-compatible | `GLM_API_KEY` |
| **DashScope** | Qwen-Plus, Qwen-Max, Qwen-Turbo | OpenAI-compatible | `QWEN_API_KEY` |
| **OpenAI** | GPT-4o, GPT-4, GPT-3.5 | OpenAI-compatible | `OPENAI_API_KEY` |

Configuration examples:

```java
// Anthropic (native API)
AnthropicProvider claude = new AnthropicProvider(apiKey);

// GLM (OpenAI-compatible)
OpenAIProvider glm = new OpenAIProvider(apiKey,
    "https://open.bigmodel.cn/api/paas/v4", "/chat/completions");

// Qwen (OpenAI-compatible)
OpenAIProvider qwen = new OpenAIProvider(apiKey,
    "https://dashscope.aliyuncs.com/compatible-mode/v1", "/chat/completions");

// Any OpenAI-compatible endpoint
OpenAIProvider custom = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
```

## Build

**Prerequisites:**
- JDK 17+
- Maven 3.8+

```bash
# Build all modules
mvn clean verify

# Run tests only
mvn test

# Run demo (mock mode, no API key needed)
mvn exec:java -pl kairo-demo \
  -Dexec.mainClass="io.kairo.demo.AgentDemo" \
  -Dexec.args="--mock"
```

## Project Status

| Metric | Value |
|--------|-------|
| Version | 0.1.0-SNAPSHOT |
| Modules | 7 (api, core, tools, multi-agent, spring-boot-starter, demo, parent) |
| Tests | 717 passed, 0 failures |
| Code Style | Spotless + Google Java Format (AOSP) |
| Coverage | JaCoCo (multi-agent at 95.5%) |
| CI/CD | GitHub Actions |
| Reactive | Project Reactor (Mono/Flux) |
| License | Apache 2.0 |

## Contributing

We welcome contributions. Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines, including code style requirements, issue/PR templates, and conventional commit conventions.

## License

Kairo is licensed under the [Apache License 2.0](./LICENSE).

```
Copyright 2025-2026 the Kairo authors.
```
