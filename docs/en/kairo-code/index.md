# Kairo Code

**Kairo Code** is a production coding agent CLI built entirely on the [Kairo framework](https://github.com/CaptainGreenskin/kairo). It serves as the primary dogfooding application — proving that Kairo's SPI-driven architecture, reactive runtime, and agent infrastructure work under real-world coding workloads.

Source code: [github.com/CaptainGreenskin/kairo-code](https://github.com/CaptainGreenskin/kairo-code)

## Why Kairo Code?

Kairo Code is not a fork of any existing coding agent. It is a **Java-native** coding agent built from the ground up on Kairo's Agent OS abstractions:

- **Framework validation** — Every feature in Kairo Code exercises a Kairo SPI. If something is missing or poorly designed, we fix the framework first. This feedback loop keeps the framework honest.
- **Java all the way down** — The agent runtime, tool execution, context management, model routing, and session persistence are all Java, running on Project Reactor. No shelling out to Python or Node for core logic.
- **Production-grade by inheritance** — Kairo Code inherits Kairo's circuit breaker, loop detection, cooperative cancellation, 6-stage context compaction, and permission guards without reimplementing them.
- **Extensible by design** — Skills, tools, model providers, and team coordinators are all pluggable via Kairo's SPI contracts. Adding a new tool or swapping a model provider requires no changes to the agent core.

## Screenshots

### CLI Demo

![CLI Demo](/images/kairo-code/video/cli-demo.gif)

### REPL Startup

![Kairo Code REPL](/images/kairo-code/repl-start.png)

### Tool Execution — Grep Search

![Tool Execution](/images/kairo-code/tool-execution.png)

### Skill System — Load & Code Review

![Skill System](/images/kairo-code/skill-system.png)

### Plan Mode — Read-only Analysis

![Plan Mode](/images/kairo-code/plan-mode.png)

### Cost Tracking

![Cost Tracking](/images/kairo-code/cost-tracking.png)

## Desktop App

Kairo Code also ships as a desktop workspace with an integrated chat agent, file explorer, source control, and self-evolution tooling.

### Main Chat Interface

![Main Chat Interface](/images/kairo-code/06-main-chat-interface.png)

### Explorer Sidebar

![Explorer Sidebar](/images/kairo-code/11-explorer-sidebar.png)

### Source Control

![Source Control Sidebar](/images/kairo-code/13-source-control-sidebar.png)

### Self-Evolution

![Self-Evolution Sidebar](/images/kairo-code/14-self-evolution-sidebar.png)

Kairo Code learns from each session — accumulating memory and knowledge, and refining behavior through the hook system:

![Self-Evolution Demo](/images/kairo-code/video/demo-evolution.gif)

### Memory Panel

![Memory Panel](/images/kairo-code/18-memory-panel.png)

### Approval Mode

![Approval Mode Dropdown](/images/kairo-code/20-approval-mode-dropdown.png)

Control how tools run with Manual, Auto-safe, and YOLO approval modes, backed by hook-based safety controls:

![Tool Approval Demo](/images/kairo-code/video/demo-approval.gif)

### Light Theme

![Light Theme Interface](/images/kairo-code/19-light-theme-interface.png)

### Agent Workspace

A full IDE-like workspace — chat agent, file explorer, source control, and theming working together:

![Agent Workspace Demo](/images/kairo-code/video/demo-team.gif)

## Key Features

| Feature | Description |
|---------|-------------|
| **Interactive REPL** | Streaming output, tool approval prompts, rich terminal rendering |
| **56 Built-in Tools** | File ops, execution, web, git, interaction, skills, agent ops, plan mode, memory, cron, code intelligence |
| **Skill System** | Markdown-based extensible capabilities loaded at runtime |
| **Multi-Agent Teams** | Spawn subagents, coordinate via TeamCoordinator SPI |
| **IDE Bridge** | WebSocket-based Bridge SPI for VS Code, JetBrains, and Zed integration |
| **Session Persistence** | FileSessionStorageProvider for conversation continuity across restarts |
| **Multi-Model Support** | Claude (native Anthropic), GLM, Qwen, GPT (OpenAI-compatible) |
| **Plan Mode** | Enter/exit plan mode for structured multi-step reasoning |
| **Memory** | Read/write/delete persistent memory at agent and team level |
| **Cron Scheduling** | Create, manage, and trigger recurring tasks |

## Tool Categories

Kairo Code ships with 56 tools organized into 11 categories:

- **File Operations** — `Read`, `Write`, `Edit`, `Glob`, `Grep`, `Tree`, `Diff`, `BatchRead`, `BatchWrite`, `SearchReplace`, `PatchApply`, `JsonQuery`, `TemplateRender`
- **Execution** — `Bash`, `Monitor`, `Mvn`, `Sleep`, `VerifyExecution`
- **Web** — `WebFetch`, `WebSearch`, `Http`, `OpenApiHttp`
- **Git** — `Git`, `Github`
- **Interaction** — `AskUser`
- **Skills** — `SkillList`, `SkillLoad`, `SkillManage`
- **Agent Operations** — `AgentSpawn`, `SendMessage`, `TeamCreate`, `TeamDelete`, `TaskCreate`, `TaskGet`, `TaskList`, `TaskUpdate`, `TodoRead`, `TodoWrite`, `Workflow`
- **Plan Mode** — `EnterPlanMode`, `ExitPlanMode`, `ListPlans`
- **Memory** — `MemoryRead`, `MemoryWrite`, `MemoryDelete`, `TeamMemoryRead`, `TeamMemoryWrite`, `TeamMemoryDelete`
- **Cron** — `CronCreate`, `CronDelete`, `CronEdit`, `CronList`, `CronPause`, `CronResume`, `CronTrigger`
- **Code Intelligence** — `Lsp`

## Supported Models

| Provider | Models | Integration |
|----------|--------|-------------|
| Anthropic | Claude Opus, Sonnet, Haiku | Native `AnthropicModelProvider` |
| GLM | GLM-4 series | OpenAI-compatible adapter |
| Qwen | Qwen series | OpenAI-compatible adapter |
| OpenAI | GPT-4o, GPT-4, etc. | OpenAI-compatible adapter |

## Relationship to Kairo Framework

Kairo Code depends on the Kairo framework as a library consumer:

```
kairo-code-core
  ├── kairo-core          (ReAct engine, compaction, model providers)
  ├── kairo-tools         (built-in tool suite)
  ├── kairo-mcp           (MCP protocol integration)
  ├── kairo-multi-agent   (team coordination)
  ├── kairo-skill         (skill system)
  └── kairo-plugin        (plugin system)
```

Kairo Code does not modify or extend the framework internals. It configures and assembles Kairo's SPIs to build a coding agent — the same way any application would use the framework.
