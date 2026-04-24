# Roadmap

| Version | Theme | Status |
|---------|-------|--------|
| v0.1–v0.4 | Core Runtime + SPI + A2A + Middleware + Snapshot | Implemented |
| v0.5 | Agents That Remember — Memory SPI + Embedding + Checkpoint/Rollback | Implemented |
| v0.6 | Agents That Are Safe — Guardrail SPI + Interrupt/Resume + Team Patterns | Planned v0.6 |
| v0.7.0 | Guardrail SPI + MCP Security + Structured Exceptions | Implemented |
| v0.7.1 | Tool Result Budget + Structured Observability | Implemented |
| v0.8 | DurableExecution MVP + ResourceConstraint SPI + Cost-Aware Routing | Implemented |
| v0.9 | Gap-Only Platform Capabilities + Channel SPI / Event Stream / OTel Exporter | Implemented |
| v0.10 | Core Refactor Waves (event bus + capability-shaped configs + hook consolidation scaffolding) | Implemented |
| v0.10.1 | Expert Team Orchestration MVP (TeamCoordinator + EvaluationStrategy SPIs + opt-in starter) | Implemented |

## v0.1–v0.4: Core Runtime (Implemented)

The foundation is in place: ReAct engine, SPI architecture, 21 built-in tools, context compaction, model providers (Anthropic, GLM, Qwen, GPT), A2A Protocol, Middleware Pipeline, Agent Snapshot, and Spring Boot integration.

## v0.5: Agents That Remember (Implemented)

Memory SPI with embedding-based retrieval, persistent checkpoint/rollback, and durable execution support.

## v0.6: Agents That Are Safe (Planned v0.6)

Guardrail SPI for input/output validation, interrupt/resume support, team collaboration patterns, and enhanced permission management.

## v0.7.0: Guardrail SPI + MCP Security (Implemented)

Guardrail SPI for 4-phase interception, MCP security with default deny-safe policy, structured error fields on KairoException, cost routing SPI, and security observability.

## v0.7.1: Tool Result Budget (Implemented)

ToolResultBudget L0 pre-truncation, structured observability metadata on ToolResult, TOOL message observability fields, tool exception/policy path classification, and ADR-010.

## v0.8: DurableExecution MVP + ResourceConstraint + Cost-Aware Routing (Implemented)

DurableExecutionStore SPI (InMemory + JDBC) for cross-process agent recovery with at-least-once semantics, ResourceConstraint SPI for unified execution enforcement (replacing scattered iteration/token/timeout checks), and CostAwareRoutingPolicy extending the v0.7 RoutingPolicy SPI with ModelTierRegistry and linear fallback chains.

## v0.9: Gap-Only Platform Capabilities + Channel SPI / Event Stream / OTel Exporter (Implemented)

The v0.9.0 GA combines two tracks:

- **Gap closure**: default self-evolution runtime wiring verified at the behavior level, execution-vs-evolution event-domain regression guards, and `kairo-core` → evolution implementation import guards.
- **Platform capability P0s**: Channel SPI + `LoopbackChannel` reference + starter + TCK (ADR-021); transport-agnostic Event Stream core with SSE and WebSocket transports behind a deny-safe `KairoEventStreamAuthorizer` (ADR-018); `KairoEventOTelExporter` in `kairo-observability` bridging `KairoEvent` to the OTel logs API with domain filter + sampling ratio + key redaction, plus a dedicated opt-in starter (ADR-022).
- **D5 deprecation closure**: `io.kairo.api.task.*` and `TeamScheduler` physically removed — prior consumers migrated to the v0.10 Expert Team coordinator and hook chain.

Release gate: `mvn clean verify` green from a clean checkout, 2,498 tests across 344 suites.

See the verification note: `docs/roadmap/v0.9-release-verification.md` (supersedes `v0.9-gap-only-verification.md`).

## v0.10: Core Refactor Waves (Implemented)

This wave is intentionally **platform-ergonomics first**: introduce a unified in-process event facade (`KairoEventBus`), capability-shaped configuration records for cross-cutting concerns, a unified hook annotation (`@HookHandler`) while keeping legacy hook annotations working, and minimal SPI scaffolding (`SkillStore`, `ProviderPipeline`) to reduce the cost of the next features (Expert Team, OTel exporters, Channel SPI).

Verification evidence: `docs/roadmap/v0.10-core-refactor-verification.md`.

## v0.10.1: Expert Team Orchestration MVP (Implemented)

The Expert Team sub-milestone lands the Anthropic-Harness-shaped Planner / Generator / Evaluator loop as first-class infrastructure. It ships a dedicated `kairo-expert-team` module plus an opt-in `kairo-spring-boot-starter-expert-team`, and exposes exactly two SPIs in `kairo-api`:

- `TeamCoordinator` — `Mono<TeamResult> execute(TeamExecutionRequest, Team)`.
- `EvaluationStrategy` — `Mono<EvaluationVerdict> evaluate(EvaluationContext)`.

Default implementations include `ExpertTeamCoordinator` (Planning → Generating → Evaluating → Terminal), `SimpleEvaluationStrategy` (deterministic rubric), `AgentEvaluationStrategy` (agent-invoker seam that maps crashes to `VerdictOutcome.REVIEW_EXCEEDED`), and `DefaultPlanner` (role-per-agent sequential plan with `PlannerFailureMode.FAIL_FAST`). A TCK (`TeamCoordinatorTCK`, `EvaluationStrategyTCK`, plus `RecordingEventBus` / `NoopMessageBus` / `StubAgent` fixtures) ships in the module's test-jar.

Key semantics:

- **Evaluator crash** under `RiskProfile.LOW` → `TeamStatus.DEGRADED` + warning; under `MEDIUM|HIGH` → `TeamStatus.FAILED`.
- **Team timeout** preserves partial step outcomes and ends on `TEAM_TIMEOUT`.
- **Event-domain isolation**: all coordinator events sit on `KairoEvent.DOMAIN_TEAM` — no leakage into execution / evolution / security domains.
- **Starter activation** requires `kairo.expert-team.enabled=true`; installing the starter alone is deliberately not enough.

Kickoff: `docs/roadmap/v0.10-expert-team-kickoff.md`. Verification evidence: `docs/roadmap/v0.10-expert-team-verification.md`.
