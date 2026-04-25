# Changelog

All notable changes to Kairo will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.1] - 2026-04-25 — ConsoleApprovalHandler cancellation safety + Kairo Code rebrand

### Changed
- **`kairo-core/.../tool/ConsoleApprovalHandler.java`** rewritten for cancellation safety. The previous
  implementation blocked on `BufferedReader.readLine()` against `System.in`, which ignores
  `Thread.interrupt()` — Reactor `dispose()` (Ctrl+C, parent-stream cancel) could not cancel a
  pending tool-approval prompt. New version uses `Mono.create` + `sink.onDispose(thread::interrupt)`
  and `reader.ready()` polling at 50 ms granularity so the read loop is interruptible.
- Constructor surface upgraded: explicit `(BufferedReader, PrintWriter)` for testability + JLine
  integration; no-arg default still wires to `System.in` / `System.out`.
- `ApprovalDecision` enum (`ALWAYS_ALLOW` / `ALWAYS_DENY`) replaces the prior `Set<String> alwaysAllowed`
  field — the prompt now offers `[a]lways` and `ne[v]er` answers symmetrically.
- `getApprovalState()` / `restoreApprovals(Map)` added to support session-resume integrations.

### Removed
- The `ConsoleApprovalHandler(Duration timeout)` and no-arg constructors backed by the old
  `Set<String>` memory are gone. Hard break — incubation-stage policy. Verified no callers in
  `kairo-*` reactor depended on the removed surface (grep across reactor returns only the file
  itself + a CHANGELOG mention).

### Added
- 18-test `ConsoleApprovalHandlerTest` in `kairo-core/src/test/java/io/kairo/core/tool/`,
  including a cancellation regression that verifies dispose interrupts a blocked reader within
  the 2 s budget.

### Companion (line B — Kairo Code repo)
- `agentcode` repo + 4 child modules + main class renamed → `kairo-code` /
  `kairo-code-{cli,core,server,examples}` / `KairoCodeMain`. POM `groupId` (`io.kairo.code`) and
  `artifactId` (`kairo-code-*`) were already correct from line B's M1 build out — this rename
  closes the directory + class-name + doc-literal gap. 5-module reactor builds clean; 42 tests
  green (M1 baseline preserved).

### Reactor
- Unchanged at 31 entries (parent + 30 modules).
- `mvn -pl kairo-core -am verify` green: 1419 tests in `kairo-core`.
- No SPI surface change — japicmp gate against `1.0.0` baseline remains green.

## [1.1.0] - 2026-04-25 — SPI Foundations for Cloud / Multi-Tenant / Code-Agent

### Added — four new SPI packages, all pure additions on top of the v1.0 GA contract
- **F1 `io.kairo.api.sandbox.*`** — `ExecutionSandbox` / `SandboxRequest` (record + Builder, 30 s
  timeout / 1 MiB output defaults) / `SandboxHandle` (AutoCloseable, hot `Flux<SandboxOutputChunk>`,
  cached `Mono<SandboxExit>`, idempotent `cancel()` / `close()`) / `SandboxOutputChunk` (sealed —
  `Stdout` / `Stderr`) / `SandboxExit` (record). Default `LocalProcessSandbox` lives in
  `kairo-tools`. 8-scenario `ExecutionSandboxTCK` is the public contract. `BashTool` refactored to
  consume the SPI; public method signatures unchanged.
- **F2 `io.kairo.api.workspace.*`** — `Workspace` / `WorkspaceProvider` / `WorkspaceKind` enum
  (`LOCAL` ships, `REMOTE_GIT` + `EPHEMERAL` reserved for v1.3) / `WorkspaceRequest`. Default
  `LocalDirectoryWorkspaceProvider` + `Workspace.cwd()` no-dep static factory mean existing
  single-workspace callers observe **zero** behavior change. Five file tools (Read / Write / Edit /
  Glob / Grep) now resolve relative paths against `currentWorkspace().root()`; absolute paths
  honoured verbatim; defence-in-depth `startsWith(root)` after `normalize()` rejects path traversal.
- **F3 `io.kairo.api.tenant.*`** — `TenantContext` record (with `SINGLE` sentinel +
  `ATTR_TENANT_ID` / `ATTR_PRINCIPAL_ID` constants) / `TenantContextHolder` (with `Scope`
  AutoCloseable + `NOOP` opt-out). Default `ThreadLocalTenantContextHolder` +
  `ReactorTenantContextPropagator`. Five passive consumption sites project tenant id without
  changing any method signature: `KairoEventBus.publish`, `KairoEventOTelExporter` (OTel
  `kairo.tenant.id` / `kairo.tenant.principal` attributes), `SecurityEventSink.record`,
  `ToolContext.tenant()`, `BridgeMeta.tenant`. Quota / isolation deferred to v1.2.
- **F4 `io.kairo.api.bridge.*`** — `BridgeRequest` / `BridgeResponse` / `BridgeMeta` /
  `BridgeRequestHandler` / `BridgeServer`. Default transport `WebSocketBridgeServer` +
  `KairoBridgeWebSocketHandler` mounted on `/ws/bridge` inside `kairo-event-stream-ws` (separate
  from event-stream channel). Frozen 5-op catalog: `agent.run` / `agent.cancel` / `agent.status` /
  `tool.approve` / `workspace.list`. Schemaless envelope, HTTP-style status codes (200/400/404/500),
  sessions never close on application-level errors.
- **ADR-025 / ADR-026 / ADR-027 / ADR-028** — all `Status: Accepted` under `docs/adr/`.

### Changed
- japicmp baseline default in `kairo-api/pom.xml` bumped from empty → `1.0.0`. Gate produces no
  break diffs (v1.1 surface is purely additive).
- `docs/governance/japicmp-policy.md` Baseline Lifecycle table promoted v1.1.0 to current state.

### Reactor
- 31 entries (parent + 30 modules) — unchanged from v1.0 GA.
- Root `pom.xml` `<revision>` remains `1.0.0-SNAPSHOT` (artifact bumps follow GA cadence, not
  SPI-foundation milestones).
- Verification doc: `docs/roadmap/V1.1-verification.md`.

## [1.0.0] - 2026-04-24 — The Java Agent Standard

### Added
- **`kairo-security-pii` module (#26 in reactor)**: Enterprise security capabilities, no new SPI
  - `PiiRedactionPolicy` implements existing `GuardrailPolicy` SPI — composable via `GuardrailChain`
  - `PiiRedactionConfig` + `PiiPattern` enum (EMAIL / US_PHONE / CREDIT_CARD / SSN / API_KEY / JWT)
  - `JdbcAuditEventSink` implements existing `SecurityEventSink` SPI — append-only `kairo_audit` table
  - Flyway `V1__create_kairo_audit_table.sql` ships in `src/main/resources/db/migration/`
  - `ComplianceReport` + `ComplianceReportCollector` (implements `Consumer<KairoEvent>`) — per-run Markdown audit-trail evidence
- **ADR-024**: PII redaction is a `GuardrailPolicy`, not a new SPI (codifies the deliberate departure from plan text)
- **`docs/roadmap/v1.0.0-ga-release-verification.md`**: Six-item closure evidence

### Changed
- Root `pom.xml` `<revision>` bumped: `0.8.0` → `1.0.0` — Maven coordinate now matches the public version

### Reactor
- 26 modules (was 25 at v0.9.1)
- `mvn -pl kairo-security-pii test` green: 26/26 (15 PII + 5 audit + 6 compliance)
- `mvn clean install -Dgpg.skip=true` green: BUILD SUCCESS for "Kairo 1.0.0", 2,551 surefire tests reactor-wide

### Notes
- Maven Central publication requires explicit user authorization for OSSRH credentials per the v1.0 plan risk register; this release does **not** auto-deploy

## [0.9.1] - 2026-04-24 — CD-train: DingTalk Channel Adapter

### Added
- **`kairo-channel-dingtalk` module**: First concrete `Channel` implementation on top of the v0.9 Channel SPI
  - `DingTalkSignatureVerifier` — HMAC-SHA256 over `timestamp\nsigningSecret` with 1-hour replay window
  - `DingTalkMessageMapper` — `DingTalk JSON ↔ ChannelMessage` with `conversationId`/`senderId` fallback
  - `DingTalkOutboundClient` — JDK-`HttpClient` POST; maps DingTalk errcodes 130101..130103 / HTTP 429 to `RATE_LIMITED`
  - `DingTalkChannel` — in-memory `msgId` dedup so replayed webhook deliveries coalesce
- **`kairo-spring-boot-starter-channel-dingtalk` starter**: Opt-in via `kairo.channel.dingtalk.enabled=true`
  - `DingTalkProperties` — `webhookUrl` / `signingSecret` / `channelId` / `atMobiles` / `outboundTimeout` / `replayWindow`
  - `DingTalkWebhookController @PostMapping /kairo/channel/dingtalk/callback` — signature-gated dispatch
  - `@ConditionalOnBean(ChannelInboundHandler.class)` deny-safe posture
- **TCK extension**: `DingTalkChannelTckTest` extends `ChannelTCK`; `DingTalkChannelExtendedScenariosTest` covers duplicate-`msgId` dedup, signature mismatch rejection, outbound HTTP 429, and DingTalk `errcode=130101` classification
- **Demo**: `kairo-examples/.../dingtalk/DingTalkBotDemo.java` showing the programmatic round-trip
- **ADR-021 addendum**: Documents DingTalk as first concrete transport — no SPI shape changes required

### Reactor
- 25 modules (was 23 at v0.10.2)
- `mvn clean verify` green: 2,525 tests / 350 suites, 0 failures, 0 errors

## [0.8.0] - 2026-04-23 — Execution Model

### Added
- **DurableExecution MVP**: Checkpoint-based crash recovery with at-least-once semantics
  - `DurableExecutionStore` SPI with InMemory and JDBC implementations
  - `ExecutionEventEmitter` with SHA-256 hash chain for event integrity verification
  - `RecoveryHandler` for automatic session recovery from latest checkpoint
  - Flyway-managed schema migration for JDBC store
  - Optimistic locking on execution records for concurrent safety
- **Idempotency contract for tool replay**:
  - `@Idempotent` / `@NonIdempotent` annotations in `io.kairo.api.tool`
  - Default-safe policy: unannotated tools treated as non-idempotent (cached result on replay)
  - `IdempotencyResolver` with deterministic key generation (SHA-256)
  - `ToolContext` extended with optional `idempotencyKey` field
- **ResourceConstraint SPI**: Unified iteration/token/timeout enforcement (renamed from ExecutionConstraint)
  - `ResourceConstraint` interface with `validate()` + `onViolation()` contract
  - `ResourceAction` enum: ALLOW, WARN_CONTINUE, GRACEFUL_EXIT, EMERGENCY_STOP
  - `DefaultResourceConstraint` consolidating checks from IterationGuards
  - Composition semantics: EMERGENCY_STOP short-circuits, severity ordering
  - End-to-end wiring: AgentConfig → AgentBuilder → ReActLoop → IterationGuards
- **Cost-Aware Routing Engine**: Budget-constrained model selection with linear fallback
  - `CostAwareRoutingPolicy` implementing `RoutingPolicy` SPI
  - `ModelTier` record with per-token pricing and latency metadata
  - `ModelTierRegistry` for configurable tier mapping
  - Spring properties: `kairo.routing.model-tiers`, `kairo.routing.fallback-chain`
- **ADR-011**: DurableExecutionStore SPI Design
- **ADR-012**: ResourceConstraint SPI Design
- **ADR-013**: Cost-Aware Routing Design

### Changed
- `IterationGuards` now delegates to `ResourceConstraint` chain (backward compatible)
- `ReActLoop` accepts optional `ExecutionEventEmitter` for event emission
- `ReasoningPhase` emits MODEL_CALL_REQUEST/RESPONSE and TOOL_CALL_REQUEST events
- `DefaultReActAgent` integrates DurableExecutionStore for persistence and recovery

### Spring Boot Configuration
- `kairo.execution.durable.enabled` — enable DurableExecution (default: false)
- `kairo.execution.durable.store-type` — "memory" or "jdbc" (default: memory)
- `kairo.execution.durable.recovery-on-startup` — auto-recover pending executions (default: true)
- `kairo.routing.model-tiers` — model tier definitions with pricing
- `kairo.routing.fallback-chain` — ordered fallback tier names

## [0.7.1] - 2026-04-23 — Tool Result Budget & Structured Observability

### Added
- **ToolResultBudget**: L0 pre-truncation for oversized tool outputs before entering conversation history
  - Content truncation with inline footer note at HookDecisionApplier / ToolPhase / ReasoningPhase / ReActLoop
  - Character count / 4 as rough token estimate (no tokenizer dependency)
- **Structured observability metadata on ToolResult**:
  - `tool_result_original_tokens`, `tool_result_kept_tokens`, `tool_result_truncated`, `tool_result_budget_reason`
- **TOOL message observability fields**:
  - `tool_result_budget_truncated_count`, `tool_result_budget_original_tokens`, `tool_result_budget_kept_tokens`, `tool_result_budget_remaining_tokens`, `tool_result_budget_per_result_tokens`
- **Tool exception/policy path classification**: `tool_execution_failed`, `tool_blocked_by_hook`, `tool_cancelled_by_hook`, `tool_skipped_by_hook`
- **ADR-010**: Tool Result Budget Contract
- Canary runbook and review documentation (`docs/runbook/`)
- v0.8 execution model kickoff documentation

## [0.7.0] - 2026-04-22 — Guardrail SPI, MCP Security & Structured Exceptions

### Added
- Exception Phase B: Structured error fields on `KairoException` (`errorCode`, `category`, `retryable`, `retryAfterMs`)
- `ErrorCategory` enum (`MODEL`, `TOOL`, `AGENT`, `STORAGE`, `SECURITY`, `UNKNOWN`)
- Guardrail SPI: `GuardrailPolicy` interface with 4-phase interception (`@Experimental`)
- `DefaultGuardrailChain`: ordered policy evaluation with DENY short-circuit
- Sealed `GuardrailPayload` with typed variants (`ModelInput`, `ModelOutput`, `ToolInput`, `ToolOutput`)
- MCP Security: `McpStaticGuardrailPolicy` with default deny-safe policy
- `McpSecurityPolicy` enum (`ALLOW_ALL`, `DENY_SAFE`, `DENY_ALL`)
- Security Observability: `SecurityEvent` record + `SecurityEventSink` SPI (`@Experimental`)
- `LoggingSecurityEventSink` (default implementation)
- Cost Routing: `RoutingPolicy` SPI + `RoutingContext`/`RoutingDecision` value objects (`@Experimental`)
- `costBudget` field on `ModelConfig`
- `DefaultRoutingPolicy` (no-op)
- ADR-007: Guardrail SPI Design
- ADR-008: Exception Phase B Structured Fields
- ADR-009: MCP Security Default Policy

### Changed
- MCP servers now default to `DENY_SAFE` security policy (previously implicitly `ALLOW_ALL`)
- `DefaultToolExecutor` accepts optional `GuardrailChain` for `PRE_TOOL`/`POST_TOOL` interception
- `ReasoningPhase` accepts optional `GuardrailChain` for `PRE_MODEL`/`POST_MODEL` interception
- `DefaultGuardrailChain` now accepts optional `SecurityEventSink` for audit event emission

### Migration Notes
- MCP users must configure `allowedTools` or set `securityPolicy: ALLOW_ALL` to restore previous behavior
- See [Upgrade Guide](docs/en/guide/upgrade-v0.6-to-v0.7.md) for detailed migration steps

## [0.5.1] - 2026-04-21 — ToolHandler SPI Uplift

### Breaking Changes
- **ToolHandler moved to kairo-api**: `ToolHandler` interface relocated from `io.kairo.core.tool.ToolHandler` to `io.kairo.api.tool.ToolHandler`
  - Migration: update import statements from `io.kairo.core.tool.ToolHandler` → `io.kairo.api.tool.ToolHandler`
- **kairo-mcp decoupled from kairo-core**: `kairo-mcp` module no longer depends on `kairo-core` — it now depends only on `kairo-api`

### Test Counts
- kairo-api: 228
- kairo-core: 1,250
- kairo-multi-agent: 96
- kairo-tools: 199
- kairo-mcp: 80 (observability)
- kairo-spring-boot-starter: 50
- spring-boot-demo: 18
- Total: 1,921, 0 failures

## [0.3.1] - 2026-04-17 — Deferred Items Cleanup

### Refactoring
- **AnthropicProvider decomposition**: Split 1132 LOC into AnthropicProvider + AnthropicHttpClient + AnthropicResponseParser
- **ModelProviderException**: Shared RateLimitException + ApiException (no longer coupled to AnthropicProvider)
- **ModelProviderUtils**: Added parseRetryAfter() shared utility

### New Features
- **Structured Output**: `ModelConfig.responseSchema(Class<?>)` + `ModelResponse.contentAs(Class<T>)` — native API enforcement (OpenAI response_format, Anthropic prompt injection + parse)
- **JsonSchemaGenerator**: Class-to-JSON-Schema via reflection (records, POJOs, primitives, enums)
- **ProviderPresets**: Factory methods for Anthropic, OpenAI, Qwen, GLM, DeepSeek
- **MCP Elicitation Protocol**: `ElicitationHandler` SPI + `AutoApproveElicitationHandler` default + Spring Boot auto-config
- **OpenAPI Tool Registration**: `OpenApiToolRegistrar` parses OpenAPI 3.x → ToolDefinition (swagger-parser 2.1.22)
- **Skill Remote Loading**: `loadFromClasspath()` + URL TTL cache (configurable, default 1hr)
- **MCP Sync API**: `McpSyncClient` blocking wrapper + `McpClientBuilder.buildSync()`
- **Span.addEvent()**: Event recording for deterministic replay (OTel delegation + StructuredLogSpan JSON)

### Test Counts
- Unit tests: 1,320 (v0.3.0: 1,191 → +129)
- Integration tests: 170 (v0.3.0: 170 → +0)
- Total: 1,490 (v0.3.0: 1,361 → +129), 0 failures

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
