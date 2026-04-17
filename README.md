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

| OS Concept | Kairo Mapping | Description |
|------------|---------------|-------------|
| Memory | Context | Context window as bounded memory with intelligent compaction |
| System Call | Tool | 21+ specialized tools — the agent's interface to the outside world |
| Process | Agent | Independent execution unit driven by a ReAct loop |
| File System | Memory | Persistent knowledge storage (file / in-memory) |
| Signal | Hook | Lifecycle event chain (Pre/Post Reasoning, Acting) |
| Executable | Skill | Plug-and-play capability packs in Markdown format |
| Job Scheduling | Task + Team | Multi-agent task orchestration and team collaboration |

Kairo is built on Project Reactor for fully reactive, non-blocking execution and supports Claude, GLM, Qwen, GPT, and other models out of the box.

## Architecture

```
kairo-parent
├── kairo-bom                  — BOM for dependency version management
├── kairo-api                  — SPI interface layer (zero implementation dependencies)
├── kairo-core                 — Core runtime (ReAct engine, compaction, model providers)
├── kairo-tools                — Built-in tool suite (21 tools)
├── kairo-mcp                  — MCP protocol integration (@Experimental)
├── kairo-multi-agent          — Multi-agent orchestration (TaskBoard, TeamScheduler)
├── kairo-spring-boot-starter  — Spring Boot auto-configuration
└── kairo-examples             — Example applications
```

## Key Features

- **ReAct Engine** — `DefaultReActAgent` implements the full Reasoning-Acting cycle with configurable iteration limits, streaming responses, and multi-layer error recovery
- **6-Stage Context Compaction** — Progressive pipeline (Snip → Micro → Collapse → Auto → Partial → CircuitBreaker) with "Facts First" strategy to preserve raw context as long as possible
- **21 Built-in Tools** — File ops (Read/Write/Edit/Glob/Grep), execution (Bash/Monitor), interaction (AskUser), skills (SkillList/SkillLoad), and agent ops (Spawn/Message/Task/Team/Plan)
- **Read/Write Partition** — READ_ONLY tools execute in parallel, WRITE/SYSTEM_CHANGE tools serialize automatically
- **Human-in-the-Loop** — Three-state permission model (ALLOWED/ASK/DENIED) with `PermissionGuard`
- **Multi-Agent Orchestration** — TaskBoard, PlanBuilder, TeamScheduler, and in-process MessageBus
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
            <groupId>io.github.captainreenskin</groupId>
            <artifactId>kairo-bom</artifactId>
            <version>0.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.captainreenskin</groupId>
        <artifactId>kairo-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.captainreenskin</groupId>
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

### 3. Spring Boot Integration

Add the starter dependency and configure via `application.yml`:

```xml
<dependency>
    <groupId>io.github.captainreenskin</groupId>
    <artifactId>kairo-spring-boot-starter</artifactId>
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
@Autowired
Agent agent;

@PostMapping("/chat")
public String chat(@RequestBody String message) {
    return agent.call(MsgBuilder.user(message)).block().getTextContent();
}
```

That's it — five lines of YAML and the agent is ready.

## Model Support

| Provider | Models | API Type | Environment Variable |
|----------|--------|----------|---------------------|
| **Anthropic** | Claude Sonnet, Claude Opus, Claude Haiku | Native Anthropic API | `ANTHROPIC_API_KEY` |
| **Zhipu AI** | GLM-4-Plus, GLM-4 | OpenAI-compatible | `GLM_API_KEY` |
| **DashScope** | Qwen-Plus, Qwen-Max, Qwen-Turbo | OpenAI-compatible | `QWEN_API_KEY` |
| **OpenAI** | GPT-4o, GPT-4, GPT-3.5 | OpenAI-compatible | `OPENAI_API_KEY` |

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

# Run tests only
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
| `MultiAgentExample` | No | TaskBoard DAG tracking + MessageBus pub/sub |
| `SessionExample` | No | FileMemoryStore + SessionSerializer round-trip |
| Spring Boot Demo | Yes | REST API, streaming, structured output, hooks, MCP |

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

Look for issues labeled [`good first issue`](https://github.com/CaptainGreenskin/kairo/labels/good%20first%20issue) to get started, or [`help wanted`](https://github.com/CaptainGreenskin/kairo/labels/help%20wanted) for more challenging tasks.

## Community

- [GitHub Discussions](https://github.com/CaptainGreenskin/kairo/discussions) — Questions, ideas, and general discussion
- [GitHub Issues](https://github.com/CaptainGreenskin/kairo/issues) — Bug reports and feature requests

<!-- TODO: Add community channels when available -->
<!-- - [Discord](https://discord.gg/xxx) -->
<!-- - WeChat / DingTalk group QR codes -->

## Acknowledgments

Kairo was inspired by the following open-source projects:

- [AgentScope Java](https://github.com/agentscope-ai/agentscope-java) (Apache 2.0, Alibaba) — Kairo's modular Maven structure and Hook lifecycle concept were inspired by AgentScope's approach to agent-oriented programming architecture. (No runtime dependency on AgentScope Java.)
- [Claude Code](https://docs.anthropic.com/en/docs/claude-code) (Anthropic) — Kairo's three-state permission model (allow/ask/deny), context compaction strategy, read/write tool partitioning, and plan mode isolation were inspired by design patterns from Anthropic's Claude Code.

Kairo extends these ideas with original contributions including the OS metaphor architecture, multi-stage context compaction pipeline, Skill system with anti-contamination design, and deep Anthropic prompt caching integration.

## License

Kairo is licensed under the [Apache License 2.0](./LICENSE).

```
Copyright 2025-2026 the Kairo authors.
```
