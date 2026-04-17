# Changelog

All notable changes to Kairo will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.0] - 2026-04-17

### Added

#### kairo-observability (new module)
- `OTelTracer` — OpenTelemetry `Tracer` SPI implementation
- `OTelSpan` — OpenTelemetry `Span` wrapper with `setAttribute`/`setStatus`/`end`
- `GenAiSemanticAttributes` — OpenTelemetry GenAI semantic conventions (gen_ai.* namespace)
- `OTelTracerFactory` — auto-configured factory for OTel SDK integration
- OpenTelemetry SDK version: 1.44.1

#### kairo-mcp
- MCP SDK upgrade: 0.17.0 → 1.1.1
- StreamableHTTP transport support (via MCP SDK native implementation)
- HTTP authentication: `headers`, `queryParams`, `bearerToken` on `McpServerConfig`
- Removed `@Experimental` annotation — MCP APIs are now stable

#### kairo-spring-boot-starter
- MCP auto-configuration with `kairo.mcp.*` property prefix
- `KairoMcpProperties` with discriminator pattern for multi-server config
- `McpAutoConfiguration` bean with conditional registration

#### kairo-core
- `CacheBreakDetector` in `AnthropicProvider` — prompt cache hit-ratio monitoring
  - `cache.hit_ratio` and `cache.broken` span attributes for observability

### Fixed
- kairo-spring-boot-starter: `DefaultToolRegistry` cast bug in auto-configuration

### Test Counts
- Unit tests: 1,191 (v0.2.1: 1,099 → +92)
- Integration tests: 170 (v0.2.1: 162 → +8)
- Total: 1,361 (v0.2.1: 1,261 → +100)

## [0.1.0-SNAPSHOT] - 2026-04-15

### Added

#### Core Architecture
- ReAct (Reasoning + Acting) agent engine with configurable iteration limits
- Model provider abstraction: AnthropicProvider, OpenAIProvider (GLM, Qwen, GPT compatible)
- Tool system with 21 built-in tools across 5 categories
- Hook system: @PreToolCall, @PostToolCall, @PreCompact, @PostCompact
- Multi-agent orchestration: Task, Team, MessageBus

#### Context Engineering
- 6-stage progressive compaction pipeline (TimeMicro → Snip → Micro → Collapse → Auto → Partial)
- Hybrid compaction thresholds (percentage + absolute buffer)
- CompactionModelFork for isolated summarization
- Post-compact recovery: file re-read, skill re-injection, MCP re-injection (50K budget)
- Prompt caching with static/dynamic system prompt boundary
- SystemPromptResult with cache_control serialization to Anthropic API
- Session memory (10K-40K cross-session persistent summaries)
- Time-based microcompact (60-minute idle trigger)
- Bidirectional partial compaction (FROM/UP_TO)

#### Production Hardening
- Tool read/write partition: READ_ONLY parallel, WRITE/SYSTEM_CHANGE serial
- Streaming tool execution: StreamingToolDetector + StreamingToolExecutor
- Multi-layer error recovery: prompt_too_long, rate_limited, server_error, max_output_tokens
- Model fallback chain with ModelFallbackManager
- Human-in-the-loop approval: 3-state permissions (ALLOWED/ASK/DENIED)
- ConsoleApprovalHandler with y/n/a interactive approval
- Plan mode isolation: write tools blocked, plan file persistence (.kairo/plans/)
- Model harness optimization: ModelCapabilityRegistry (14 models), ComplexityEstimator, ToolDescriptionAdapter
- Session persistence: FileMemoryStore with atomic writes, SessionManager with TTL cleanup
- Token intelligence: ModelRegistry, TokenBudgetManager with API usage feedback

#### Infrastructure
- Apache 2.0 licensing on all source files
- Spotless (Google Java Format AOSP) + JaCoCo coverage
- GitHub Actions CI workflow
- 717 unit tests across 7 modules

### Fixed
- OpenAI streaming tool parser: tool call ID/name confusion for GLM/Qwen
- Multi-tool flush in streaming: all pending tool calls now properly ended
- Streaming re-execution prevention: pre-computed results reused in processModelResponse

### Models Verified
- Zhipu GLM (glm-4-plus) — E2E verified with streaming
- Alibaba Qwen (qwen-plus) — E2E verified with streaming
- Mock provider — unit and integration tests
