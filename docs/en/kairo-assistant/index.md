# Kairo Assistant

**Kairo Assistant** is a self-hosted, multi-channel personal AI assistant built entirely on the Kairo framework. While [Kairo Code](/en/kairo-code/) focuses on coding, Kairo Assistant proves the framework can power a full-featured personal assistant with 50+ tools, 8 messaging channels, self-evolution, and multi-agent orchestration.

> **Status:** Early development (`0.1.0-SNAPSHOT`). Expect breaking changes.

## Why Kairo Assistant?

- **Self-hosted** — Run on your own infrastructure. Your data never leaves your control.
- **Multi-channel** — Connect to DingTalk, Feishu/Lark, Slack, Telegram, Discord, Mattermost, or any webhook.
- **Multi-model** — Switch between Claude, GPT, GLM, DeepSeek, Qwen, Gemini, Kimi, Groq, Ollama, and 10+ more providers.
- **OpenAI-compatible API** — Drop-in replacement at `/v1/chat/completions` and `/v1/responses` with streaming.
- **Framework dogfood** — Exercises every Kairo SPI (sandbox, workspace, plugin, gateway, evolution, expert-team, MCP) in a real product.

## Core Capabilities

### 50+ Built-in Tools

| Category | Tools |
|----------|-------|
| **Personal** | Notes, Bookmarks, Contacts, Calendar, Reminders, Todo, User Profile, Clipboard, Time |
| **File Ops** | Read, Write, Patch, ListDirectory, SearchFiles, FileCheckpoint |
| **Execution** | Shell, CodeExecute, Process, Env, Git |
| **Web** | WebFetch, WebSearch, Browser (Playwright), HttpRequest |
| **AI / Media** | ImageGen (DALL-E), Vision, Voice (TTS/STT), VideoGenerate, ComputerUse, Screenshot |
| **Multi-Agent** | DelegateTask, ExpertTeam, MixtureOfAgents, Kanban, SubagentCoordinator, SendMessage |
| **Data** | Calculator, Encode, Json, Text, SystemInfo, Weather, HomeAssistant |
| **Advanced** | Cron, Goal, McpClient, MemorySearch, SessionSearch, Workflow, SkillCreate, SkillHub, PTC |

### 8 Messaging Channels

| Channel | Integration Type |
|---------|-----------------|
| DingTalk | Webhook + Stream Mode SDK |
| Feishu / Lark | Webhook + WebSocket SDK |
| Slack | Incoming Webhook |
| Telegram | Webhook |
| Discord | Incoming Webhook + Bot |
| Mattermost | Incoming Webhook |
| Generic Webhook | Any HTTP endpoint |

### Programmatic Tool Calling (PTC)

Write Python scripts that call tools via JSON-RPC — collapse multi-step tool chains into a single turn:

```python
# PTC script: tools are available as functions
files = glob("src/**/*.java")
for f in files:
    content = read_file(f)
    if "deprecated" in content.lower():
        print(f"Found deprecated usage in {f}")
```

### Multi-Agent Kanban

Built-in Kanban board with dependency tracking, swarm topology, circuit breaker, and dispatcher daemon for complex multi-agent workflows.

### Self-Evolution

The agent learns from interactions: creates reusable skills via `skill_create`, a curator promotes them through DRAFT → VALIDATED → TRUSTED, and lessons learned feed back into the system prompt.

### Goals & Scheduling

Cron-based recurring goals with channel delivery — set up daily briefings, weekly reports, or periodic checks that run automatically and deliver results to your preferred channel.

### React Admin Console

Web-based management UI (Vite + React + Tailwind) for:
- Session management and conversation history
- Cron task scheduling and monitoring
- Plugin management (install/enable/disable)
- Self-evolution monitoring and skill curation
- Usage analytics and system prompt editing

## Admin Console

The React admin console provides full visibility and control over the assistant — sessions, tasks, skills, memory, tools, and observability.

![Console Demo](/images/kairo-assistant/video/console-demo.gif)

### Dashboard

![Dashboard](/images/kairo-assistant/console-dashboard.png)

### Chat

![Chat](/images/kairo-assistant/console-chat.png)

### Sessions

![Sessions](/images/kairo-assistant/console-sessions.png)

### Tasks & Kanban Board

![Tasks](/images/kairo-assistant/console-tasks.png)

![Kanban Board](/images/kairo-assistant/console-board.png)

### Skills & Self-Evolution

![Skills](/images/kairo-assistant/console-skills.png)

![Self-Evolution](/images/kairo-assistant/console-evolution.png)

### Memory

![Memory](/images/kairo-assistant/console-memory.png)

### Tools

![Tools](/images/kairo-assistant/console-tools.png)

### Analytics & Observability

![Analytics](/images/kairo-assistant/console-analytics.png)

![Observability](/images/kairo-assistant/console-observability.png)

## Architecture

```
kairo-assistant/
├── kairo-assistant-core      — Agent factory, 50+ tools, channels, security, memory, goals, i18n
├── kairo-assistant-cli       — Interactive REPL (60+ slash commands), one-shot, daemon, ACP server
└── kairo-assistant-server    — Spring Boot REST/WS/SSE + React admin console + OpenAI-compatible API
```

### Three Run Modes

| Mode | Command | Description |
|------|---------|-------------|
| **Interactive REPL** | `kairo-assistant` | 60+ slash commands, streaming, tool approval |
| **One-shot** | `kairo-assistant -p "query"` | Single query, stdout result, exit |
| **Server** | `kairo-assistant-server` | REST + WebSocket + SSE with admin console |
| **ACP Server** | `kairo-assistant --acp-server` | Stdio JSON-RPC for editor integration |

### Kairo Framework Dependencies

Kairo Assistant imports 11 Kairo modules:

`kairo-core` · `kairo-tools` · `kairo-cron` · `kairo-skill` · `kairo-plugin` · `kairo-mcp` · `kairo-expert-team` · `kairo-multi-agent` · `kairo-evolution` · `kairo-security-pii` · `kairo-observability`

### Sandbox Backends

| Backend | Description |
|---------|-------------|
| `LocalSandbox` | Direct process execution (default) |
| `DockerSandbox` | Isolated Docker container |
| `SshSandbox` | Remote execution over SSH |
| `DaytonaSandbox` | Daytona workspace integration |

## Kairo Assistant vs Kairo Code

| Aspect | Kairo Assistant | Kairo Code |
|--------|----------------|------------|
| **Purpose** | General-purpose personal AI assistant | Coding-specific agent |
| **Tools** | 50+ (productivity, media, home automation) | 56 (file ops, git, code-focused) |
| **Channels** | 8 messaging platforms | Terminal / IDE only |
| **Multi-agent** | Kanban board, swarm, dispatcher | Team coordination |
| **Goals** | Cron-based recurring goals | Not present |
| **PTC** | Python scripts calling tools via RPC | Not present |
| **Server** | Full REST/WS/SSE + React admin + OpenAI API | Bridge SPI |
| **Sandbox** | 4 backends (local/Docker/SSH/Daytona) | LocalProcess |
| **Persona** | SOUL.md personality files | CLAUDE.md project instructions |

## Links

- [GitHub Repository](https://github.com/CaptainGreenskin/kairo-assistant)
- [Kairo Framework](/en/guide/introduction)
- [Kairo Code](/en/kairo-code/)
