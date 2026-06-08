# Architecture

## Module Structure

Kairo Code is organized into four modules:

```
kairo-code/
├── kairo-code-cli         — REPL interface, terminal rendering, command dispatch
├── kairo-code-core        — Agent configuration, tool wiring, skill loading, session management
├── kairo-code-server      — Bridge SPI server for IDE integration (WebSocket)
└── kairo-code-examples    — Demo configurations and usage examples
```

### kairo-code-cli

The user-facing terminal layer. Responsibilities:

- **REPL loop** — Reads user input, dispatches to the agent, renders streaming output
- **Terminal rendering** — Markdown formatting, syntax highlighting, tool approval prompts
- **Command dispatch** — Parses slash commands (`/plan`, `/skills`, `/clear`, `/exit`) and routes them

### kairo-code-core

The agent assembly layer. This module wires together Kairo framework SPIs into a functioning coding agent:

- **Agent configuration** — Builds a `DefaultReActAgent` with the appropriate model provider, tools, skills, and hooks
- **Tool wiring** — Registers all 56 built-in tools with the `DefaultToolRegistry` and configures permission guards
- **Skill loading** — Loads Markdown-based skills from the workspace and user-level directories
- **Session management** — Configures `FileSessionStorageProvider` for conversation persistence

### kairo-code-server

A WebSocket server that exposes the coding agent via the Bridge SPI, enabling IDE integration:

- **VS Code** — Extension connects over WebSocket
- **JetBrains** — Plugin connects over WebSocket
- **Zed** — Agent Client Protocol (ACP) over stdio

### kairo-code-examples

Sample configurations demonstrating different setups: model selection, custom tool registration, skill authoring, team coordination patterns.

## Framework Dependencies

Kairo Code consumes the Kairo framework as a library. The dependency graph:

```
kairo-code-cli
└── kairo-code-core
    ├── kairo-core              — ReAct engine, context compaction, model providers
    ├── kairo-tools             — 17+ framework-level tools (extended to 56 in kairo-code)
    ├── kairo-mcp               — MCP protocol for external tool servers
    ├── kairo-multi-agent       — A2A protocol, TeamCoordinator, MessageBus
    ├── kairo-skill             — Skill registry and loader
    ├── kairo-plugin            — Plugin system (Claude Code format compatible)
    └── kairo-observability     — OpenTelemetry tracing (optional)

kairo-code-server
├── kairo-code-core
└── kairo-event-stream-ws       — WebSocket transport for Bridge SPI
```

## REPL Loop Flow

The REPL follows a straightforward pipeline:

```
User Input
    │
    ▼
Command Dispatch ──── slash command? ──── handle locally (e.g. /plan, /clear)
    │
    │ (natural language)
    ▼
Agent.call(userMessage)
    │
    ▼
┌─────────────────────────────────────────────┐
│              ReAct Loop (kairo-core)         │
│                                             │
│   Reasoning ──► Tool Selection ──► Execute  │
│       ▲                              │      │
│       └──────── Observation ◄────────┘      │
│                                             │
│   Repeats until: answer ready / max iters   │
└─────────────────────────────────────────────┘
    │
    ▼
Streaming Output ──► Terminal Renderer
    │
    ▼
Tool Approval (if needed) ──► User confirms ──► continue loop
    │
    ▼
Final Response ──► display + persist session
```

Each iteration of the ReAct loop:

1. **Reasoning phase** — The model receives the conversation context and produces a thought + tool call (or final answer)
2. **Tool phase** — The selected tool is executed via `DefaultToolExecutor`, subject to permission guards
3. **Observation phase** — The tool result is appended to the context as an observation
4. **Compaction check** — If the context exceeds token budget, the 6-stage compaction engine (Snip, Micro, Collapse, Auto, Partial, CircuitBreaker) reduces it

## Bridge SPI for IDE Integration

The Bridge SPI allows external clients (IDEs, editors) to drive a Kairo Code agent over a transport layer:

```
┌──────────────┐     WebSocket      ┌───────────────────┐
│   VS Code    │◄──────────────────►│                   │
│   Extension  │                    │  kairo-code-server │
└──────────────┘                    │  (Bridge SPI)      │
                                    │                   │
┌──────────────┐     WebSocket      │  ┌─────────────┐  │
│  JetBrains   │◄──────────────────►│  │ kairo-code-  │  │
│   Plugin     │                    │  │   core       │  │
└──────────────┘                    │  └─────────────┘  │
                                    └───────────────────┘
┌──────────────┐     stdio (ACP)
│     Zed      │◄──────────────────► kairo-code-cli (ACP mode)
└──────────────┘
```

The Bridge SPI translates IDE requests (open file, run command, apply edit) into Kairo agent calls and streams results back.

## Session Lifecycle

```
Start CLI
    │
    ▼
Load session from .kairo/sessions/ (if exists)
    │
    ▼
Initialize Agent (model provider, tools, skills, hooks)
    │
    ▼
REPL Loop ◄──────────────────────────┐
    │                                │
    ├── process input                │
    ├── run agent                    │
    ├── persist session snapshot     │
    └── prompt for next input ───────┘
    │
    ▼
/exit ──► save final session ──► shutdown
```

Session data includes:
- Conversation history (messages with roles)
- Tool approval preferences for the session
- Active plan state (if in plan mode)
- Memory entries created during the session
