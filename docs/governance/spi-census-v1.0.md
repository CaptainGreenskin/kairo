# SPI Census — v1.0.0-RC1

**Purpose**: Frozen triage of every type under `io.kairo.api.*` with proposed final stability
status for v1.0 GA. This document is the authoritative input to the annotation pass
(`@Stable` / `@Experimental` / `@Internal` / delete-or-move).

**Baseline**: `kairo-api/src/main/java/io/kairo/api/**/*.java` at 2026-04-24 — 201 Java files
across 22 packages.

**Policy**:

- `@Stable` — frozen across minor releases; breaking changes only at major version boundaries.
  Target: ~70% of public types. These are the surfaces we commit to for v1.0 → v2.0.
- `@Experimental` — functional but shape may change. Target: ~20%. Newer SPIs that need real-world
  usage feedback before we can freeze them.
- `@Internal` — lives in `io.kairo.api` for packaging reasons but not part of the contract. No
  stability guarantee. Target: ~10%. Use sparingly.
- Delete / move — types that should not be in `io.kairo.api` at all for v1.0.

The stability decision for each package follows the **usage-first principle**: a type stabilizes
only if (a) it has been on the current shape for at least one release without breaking change,
(b) it has at least one consumer outside `kairo-core`, (c) we can write a migration note for
any future change without breaking users.

---

## Summary

| Package | Types | @Stable | @Experimental | @Internal | Delete |
|---------|------:|--------:|--------------:|----------:|-------:|
| (root) | 3 | 3 | 0 | 0 | 0 |
| agent | 12 | 9 | 3 | 0 | 0 |
| a2a | 6 | 0 | 6 | 0 | 0 |
| channel | 7 | 0 | 7 | 0 | 0 |
| context | 12 | 12 | 0 | 0 | 0 |
| event | 2 | 0 | 2 | 0 | 0 |
| event.stream | 5 | 0 | 5 | 0 | 0 |
| evolution | 9 | 0 | 9 | 0 | 0 |
| exception | 11 | 11 | 0 | 0 | 0 |
| execution | 9 | 9 | 0 | 0 | 0 |
| guardrail | 9 | 0 | 9 | 0 | 0 |
| hook | 26 | 26 | 0 | 0 | 0 |
| mcp | 3 | 3 | 0 | 0 | 0 |
| memory | 5 | 5 | 0 | 0 | 0 |
| message | 3 | 3 | 0 | 0 | 0 |
| middleware | 5 | 0 | 5 | 0 | 0 |
| model | 14 | 12 | 2 | 0 | 0 |
| plan | 2 | 2 | 0 | 0 | 0 |
| routing | 3 | 0 | 3 | 0 | 0 |
| skill | 5 | 4 | 1 | 0 | 0 |
| team | 18 | 0 | 18 | 0 | 0 |
| tool | 21 | 21 | 0 | 0 | 0 |
| tracing | 4 | 4 | 0 | 0 | 0 |
| **Total** | **192** | **119** | **70** | **0** | **0** |

> Counts exclude `package-info.java` files (3 total — channel, event.stream, team).

**Distribution**: 62% `@Stable`, 36% `@Experimental`, 0% `@Internal`, 0% delete.

**Commentary on the 70/20/10 target**: The ideal mix was 70 / 20 / 10. We land at 62 / 36 / 0
because:

1. **No @Internal candidates**: the v0.10 cleanup waves (B1.1-B1.4) already physically moved
   genuinely internal types out of `io.kairo.api`. Every remaining type has an externally
   meaningful contract. Keeping the `@Internal` bucket at 0 avoids polluting the public API
   with hedged surfaces.
2. **Large experimental bucket**: Four full subsystems — team (v0.10.1 MVP), evolution (v0.9.0
   wiring proof), channel (v0.9.0 + 1 concrete transport in v0.9.1), guardrail (v0.7.0 but
   limited real-world adoption) — are still accumulating usage signal. Declaring them stable
   in v1.0 would freeze shapes before the feedback has arrived.
3. **Deferred freeze**: team/evolution/channel/guardrail will land at `@Stable` in v1.1 after
   two minor releases of real-world consumption. This is explicit — not a missed target.

---

## Stable (frozen for v1.0.0 → v2.0.0)

### Root (3 types)

| Type | Rationale |
|------|-----------|
| `io.kairo.api.Experimental` | Stability annotation — shape must be stable for the rule to work |
| `io.kairo.api.Stable` | Stability annotation (new in v1.0) |
| `io.kairo.api.Internal` | Stability annotation (new in v1.0) |

### agent (9 stable / 3 experimental)

**Stable**:

| Type | Since | Rationale |
|------|-------|-----------|
| `Agent` | v0.1 | Core entry point; shape unchanged since v0.4 |
| `AgentConfig` | v0.1 | Record; v0.10.2 removed deprecated MCP fields; shape now clean |
| `AgentFactory` | v0.4 | Used by starter DI |
| `AgentState` | v0.4 | Enum with clear semantics |
| `AgentSnapshot` | v0.4 | Persistence format; shape stable since v0.5 |
| `SnapshotStore` | v0.4 | Three implementations (memory / file / JDBC) have been compatible |
| `CancellationSignal` | v0.4 | Single-method interface; no shape risk |

**Experimental** (new or still stabilizing):

| Type | Rationale |
|------|-----------|
| `AgentBuilderCustomizer` | v0.9.0 — Spring injection shim; keep experimental pending usage feedback |
| `DurableCapabilityConfig` | v0.10.0 — capability-shaped config still new |
| `LoopDetectionConfig` | v0.10.0 — capability-shaped config still new |
| `McpCapabilityConfig` | v0.10.0 — capability-shaped config still new |
| `SystemPromptContributor` | v0.9.0 — new extension point |

### context (12 stable)

Entire package is stable — 6-stage compaction pipeline has been the Kairo differentiator since
v0.1.0 and its contract types have not changed shape.

| Type | Role |
|------|------|
| `BoundaryMarker` | Cache boundary marker enum |
| `CacheScope` | Prompt cache scope enum |
| `CompactionConfig` | Compaction thresholds record |
| `CompactionResult` | Compaction output record |
| `CompactionStrategy` | Strategy enum (TimeMicro → Partial) |
| `ContextBuilder` | Builder SPI |
| `ContextBuilderConfig` | Builder config record |
| `ContextEntry` | Content entry record |
| `ContextManager` | Central context SPI |
| `ContextSource` | Source enum (USER / TOOL / etc.) |
| `ContextState` | State enum |
| `SystemPromptSegment` | System prompt segment record |
| `TokenBudget` | Budget record |

### exception (11 stable)

| Type | Role |
|------|------|
| `KairoException` | Base class with structured fields (since v0.7) |
| `ErrorCategory` | Error category enum |
| `AgentException` / `AgentExecutionException` / `AgentInterruptedException` | Agent errors |
| `ModelException` / `ModelApiException` / `ModelRateLimitException` / `ModelTimeoutException` | Model errors |
| `ToolException` / `ToolPermissionException` | Tool errors |
| `MemoryStoreException` | Memory errors |
| `PlanModeViolationException` | Plan mode errors |

### execution (9 stable)

v0.8.0 Durable Execution Model — contract frozen since ADR-011/012.

| Type | Role |
|------|------|
| `DurableExecution` | Execution contract interface |
| `DurableExecutionStore` | Persistence SPI |
| `ExecutionEvent` / `ExecutionEventType` | Event record + type enum |
| `ExecutionStatus` | Status enum |
| `ResourceConstraint` / `ResourceAction` / `ResourceContext` / `ResourceValidation` | v0.8 resource constraint SPI |

### hook (26 stable)

v0.10.0 consolidated the hook system: both legacy annotations (`@PreToolCall` era) and new
unified `@HookHandler` path are contractually stable. No shape changes expected for v1.0.

Includes: `HookChain` / `HookResult` / `HookPhase` / `HookHandler` / `HookEvent`;
event records (`PreActingEvent`, `PostActingEvent`, `PreReasoningEvent`, etc.); legacy
marker annotations (`PreActing`, `PostActing`, `PreReasoning`, etc.).

### mcp (3 stable)

| Type | Role |
|------|------|
| `McpPlugin` | MCP plugin SPI |
| `McpPluginRegistration` | Registration record |
| `McpPluginTool` | Tool record |

### memory (5 stable)

| Type | Role |
|------|------|
| `MemoryStore` | Persistence SPI (since v0.5) |
| `MemoryEntry` / `MemoryQuery` / `MemoryScope` | Value types |
| `EmbeddingProvider` | Embedding SPI |

### message (3 stable)

| Type | Role |
|------|------|
| `Msg` | Message record |
| `MsgRole` | Role enum |
| `Content` | Content sealed interface |

### model (12 stable / 2 experimental)

**Stable**:

| Type | Role |
|------|------|
| `ModelProvider` | Core provider SPI (since v0.1) |
| `ModelConfig` / `ModelResponse` | Config + response records |
| `ModelCapability` | Capability enum |
| `ApiException` / `ApiErrorType` / `ClassifiedError` | Error taxonomy |
| `ModelUnavailableException` | Unavailable signal |
| `RawStreamingModelProvider` | Streaming provider SPI |
| `RetryConfig` | Retry config |
| `StreamChunk` / `StreamChunkType` | Stream contract |
| `IntRange` / `ToolVerbosity` | Value types |

**Experimental**:

| Type | Rationale |
|------|-----------|
| `ProviderPipeline` | v0.10.2 — SPI decomposition; two implementations (Anthropic + OpenAI) — keep experimental pending third-party adoption |

### plan (2 stable)

`PlanFile` / `PlanStatus` — plan mode contract since v0.4.

### skill (4 stable / 1 experimental)

**Stable**:

- `SkillCategory` / `SkillDefinition` / `SkillRegistry` / `TriggerGuard` — skill subsystem since v0.3.2.

**Experimental**:

- `SkillStore` — introduced in v0.10.0; usage feedback pending.

### tool (21 stable)

Entire tool subsystem is stable — it is the oldest Kairo contract, unchanged in shape since v0.1.
Includes: `Tool` / `ToolHandler` / `ToolDefinition` / `ToolInvocation` / `ToolResult` /
`ToolExecutor` / `ToolRegistry` / `ToolContext` / `ToolCallRequest` / `ToolParam` /
`ToolCategory` / `ToolPermission` / `ToolSideEffect` / `PermissionDecision` / `PermissionGuard` /
`ApprovalResult` / `UserApprovalHandler` / `JsonSchema` / `StreamingToolResultCallback` /
`Idempotent` / `NonIdempotent`.

### tracing (4 stable)

`Tracer` / `Span` / `NoopTracer` / `NoopSpan` — since v0.3.

---

## Experimental (deferred to v1.1+)

### a2a (6 experimental)

`A2aClient` / `A2aException` / `A2aNamespaces` / `AgentCard` / `AgentCardResolver` /
`AgentSkill` — A2A protocol binding introduced in v0.4.0 but has limited production usage.
Shape will stabilize after at least one real-world consumer lands outside the reference impl.

### channel (7 experimental)

`Channel` / `ChannelAck` / `ChannelFailureMode` / `ChannelIdentity` / `ChannelInboundHandler` /
`ChannelMessage` / `ChannelOutboundSender` — v0.9.0 SPI, v0.9.1 DingTalk is the first concrete
transport. Need at least one more transport (Feishu / Slack) to validate shape before freeze.

### event (2 experimental)

`KairoEvent` / `KairoEventBus` — v0.10.0 event facade. Wired as default in v0.10.2 but
subscriber-side usage is new; keep experimental through v1.0.

### event.stream (5 experimental)

`BackpressurePolicy` / `EventStreamFilter` / `EventStreamSubscription` /
`EventStreamSubscriptionRequest` / `KairoEventStreamAuthorizer` — v0.9.0 stream SPI.
SSE and WS transports exist; shape needs real-world subscriber feedback.

### evolution (9 experimental)

`EvolutionConfig` / `EvolutionContext` / `EvolutionCounters` / `EvolutionOutcome` /
`EvolutionPolicy` / `EvolutionTrigger` / `EvolvedSkill` / `EvolvedSkillStore` /
`SkillTrustLevel` — self-evolution scaffolding from v0.9.0. Still maturing.

### guardrail (9 experimental)

`GuardrailChain` / `GuardrailContext` / `GuardrailDecision` / `GuardrailPayload` /
`GuardrailPhase` / `GuardrailPolicy` / `SecurityEvent` / `SecurityEventSink` /
`SecurityEventType` — v0.7.0 SPI marked experimental; limited adoption prevents freeze.

### middleware (5 experimental)

`Middleware` / `MiddlewareChain` / `MiddlewareContext` / `MiddlewareOrder` /
`MiddlewareRejectException` — v0.4.0 but low adoption. Keep experimental pending redesign
consideration now that `HookHandler` covers many use cases.

### routing (3 experimental)

`RoutingContext` / `RoutingDecision` / `RoutingPolicy` — v0.7.0 cost-routing SPI.
`DefaultRoutingPolicy` is a no-op; only `CostAwareRoutingPolicy` consumes it. Need more
routing strategies to validate shape.

### team (18 experimental)

Entire team package is v0.10.1 Expert Team MVP — 7 MVP ITs pass, but MVP by definition
means the shape is a proposal, not yet a contract. Covers:

`EvaluationContext` / `EvaluationStrategy` / `EvaluationVerdict` / `EvaluatorPreference` /
`HandoffMessage` / `MessageBus` / `PlannerFailureMode` / `RiskProfile` / `RoleDefinition` /
`Team` / `TeamConfig` / `TeamCoordinator` / `TeamEvent` / `TeamEventType` /
`TeamExecutionPlan` / `TeamExecutionRequest` / `TeamManager` / `TeamResourceConstraint` /
`TeamResult` / `TeamStatus` / `TeamStep`.

---

## Deferred decisions

None — every type has a clear stable-or-experimental verdict. `@Internal` bucket is
intentionally empty (no packaging-only leakage remains post-v0.10 cleanup). Delete/move
bucket is empty (prior cleanups already removed candidates: `io.kairo.api.task.*`,
`TeamScheduler`, MCP record fields).

---

## Application plan

This document freezes the intent. The annotation pass follows in a separate PR:

1. **Phase 1** — Bulk annotate all `@Stable` types listed above.
2. **Phase 2** — Ensure every `@Experimental` type actually carries `@Experimental` (cross-check
   against `rg "@Experimental"` census; fill gaps).
3. **Phase 3** — Enforcement: `japicmp-maven-plugin` wired into `kairo-api` release build.
   `@Stable`-surface breaking changes fail the build. `@Experimental` surfaces pass through.

See `docs/governance/spi-annotation-application.md` (to be written alongside Phase 1).

---

## Post-v1.0 commitments

- **v1.1 freeze pass**: re-audit the 70 experimental types based on real-world feedback.
  Expected: team + channel + evolution + event.stream promote to stable.
- **v2.0 breaking window**: the only place where `@Stable` surfaces may change. Major version
  cadence is deliberately slow — target one every 18-24 months.

## Related documents

- `.plans/VERSION-STATUS-SOT.md` — v1.0.0 row (Draft → Released across Wave 3A-3C)
- `docs/roadmap/v1.0.0-ga-release-verification.md` — to be written at GA cut
- `kairo-api/src/main/java/io/kairo/api/Stable.java` — `@Stable` annotation source
- `kairo-api/src/main/java/io/kairo/api/Experimental.java` — `@Experimental` annotation source
- `kairo-api/src/main/java/io/kairo/api/Internal.java` — `@Internal` annotation source
