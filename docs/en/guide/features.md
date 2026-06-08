# Features

## Key Features

- **ReAct Engine** — `DefaultReActAgent` implements the full Reasoning-Acting cycle with configurable iteration limits, streaming responses, and multi-layer error recovery
- **6-Stage Context Compaction** — Progressive pipeline (Snip → Micro → Collapse → Auto → Partial → CircuitBreaker) with "Facts First" strategy to preserve raw context as long as possible
- **56 Built-in Tools** — File ops (Read/Write/Edit/Glob/Grep/Tree/Diff/BatchRead/BatchWrite/SearchReplace/PatchApply/JsonQuery/TemplateRender), execution (Bash/Monitor/Mvn/Sleep/VerifyExecution), web (WebFetch/WebSearch/Http/OpenApiHttp), git (Git/Github), interaction (AskUser), skills (SkillList/SkillLoad/SkillManage), agent ops (AgentSpawn/SendMessage/TeamCreate/TeamDelete/Task*/Todo*/Workflow), plan mode (EnterPlanMode/ExitPlanMode/ListPlans), memory (Memory*/TeamMemory*), cron (Cron*), and code intelligence (Lsp)
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
- **Plugin System** — Claude Code format compatible plugin system with 5 install sources (LocalPath/GitHub/GitUrl/GitSubdir/Npm)
- **Gateway** — Multi-channel routing, session management, streaming, mirroring via Gateway SPI
- **Execution Sandbox** — ExecutionSandbox SPI with LocalProcessSandbox default
- **Workspace Provider** — WorkspaceProvider SPI with path-traversal defense
- **Tenant Context** — TenantContextHolder with Reactor context propagation
- **Bridge SPI** — WebSocket-based IDE integration (5-op catalog)
- **ACP** — Agent Client Protocol for editor integration (JSON-RPC over stdio)
- **LSP Diagnostics** — Post-edit baseline diff ("did this edit introduce new errors?")
- **Cron Scheduler** — Built-in scheduled task execution

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

## Demo Examples

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

