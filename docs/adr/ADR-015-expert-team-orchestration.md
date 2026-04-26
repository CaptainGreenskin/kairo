# ADR-015: Expert Team Orchestration

## Status
Accepted (v0.10)

## Context

Kairo's multi-agent story at v0.9 consisted of a low-level primitive set — `TeamScheduler`,
`TaskBoard`, and `MessageBus` — exposed directly as the public API. These primitives can
compose an expert-team workflow, but every adopter has to reinvent the same choreography:

1. Decompose a user request into steps.
2. Assign each step to a role-bound agent.
3. Evaluate the artifact against a rubric.
4. Loop back on REVISE, escalate on REVIEW_EXCEEDED, abort on timeout.

At the same time the existing `TeamScheduler` / `TaskBoard` API hard-codes a single
choreography (push tasks from a board to agents) and leaks it into every caller, which
blocks alternative patterns (ReAct coordinator, plan-act-evaluate, human-in-the-loop).

Kairo needs a higher-level **Expert Team** orchestration that:

- Encapsulates the plan → generate → evaluate → feedback loop as a deterministic-first,
  auditable pipeline.
- Exposes a pluggable coordinator SPI so that deterministic, advisory-LLM, and full-ReAct
  coordinators can coexist behind one contract (see ADR-016).
- Treats evaluation as a first-class, replaceable strategy (Simple rubric today, Agent-based
  judge tomorrow).
- Is packaged as an **optional module** — enterprises that do not want expert-team
  orchestration must be able to exclude it entirely from the classpath, mirroring the
  evolution subsystem from ADR-014.

## Decision

### Module structure

- `kairo-expert-team`: Independent module containing `ExpertTeamCoordinator`,
  `ExpertTeamStateMachine`, the two evaluation strategies, and `TeamResourceConstraint`.
  Depends only on `kairo-api` and `kairo-core` — **kairo-core has ZERO dependency on this
  module**.
- `kairo-spring-boot-starter-expert-team`: Standalone Spring Boot starter that
  auto-configures the expert-team subsystem. Separate starter (not aggregated into
  `kairo-spring-boot-starter`), identical pattern to
  `kairo-spring-boot-starter-evolution`.

### Retire the legacy team/task API

`TeamScheduler`, `TaskBoard`, `Task`, `TaskStatus`, and `Plan` were deprecated in v0.10
core-refactor waves. v0.10 Expert Team removes them entirely:

- `TeamScheduler` is replaced by the `TeamCoordinator` SPI (see ADR-016). The default
  task-dispatch implementation (`DefaultTaskDispatchCoordinator`) lives in
  `kairo-multi-agent` as an internal detail, not a public contract.
- `TaskBoard` becomes a package-private implementation detail of
  `DefaultTaskDispatchCoordinator`. Callers that want task-board semantics compose them
  through the coordinator.
- `Task` / `TaskStatus` / `Plan` are removed; expert-team callers use
  `TeamExecutionPlan` / `TeamStep` / `EvaluationVerdict` instead.

The incubation-stage rule applies: no compatibility shims, no deprecation bridge. Callers
update to the new API in a single wave.

### Lifecycle state machine

`ExpertTeamStateMachine` defines the canonical flow:

```
IDLE -> PLANNING -> GENERATING -> EVALUATING -> {GENERATING (revise) | COMPLETED | FAILED | DEGRADED}
                                                                      \-> TIMEOUT (terminal)
```

- **PLANNING**: produce `TeamExecutionPlan` from user request.
- **GENERATING**: dispatch current `TeamStep` to the role-bound agent.
- **EVALUATING**: run `EvaluationStrategy` to produce `EvaluationVerdict`.
- **DEGRADED**: terminal success-with-warning, reached when feedback rounds are exceeded
  under LOW risk profile.

### Failure semantics

Fixed at contract level, configurable at policy level:

| Phase     | Default behaviour                                                                 |
| --------- | --------------------------------------------------------------------------------- |
| Planner   | `FAIL_FAST`. Opt-in `SINGLE_STEP_FALLBACK` for resilience.                        |
| Generator | `RETRY(N)` → `DOWNGRADE_MODEL` → `SKIP_STEP` → `ABORT_TEAM`.                      |
| Evaluator | **`REVIEW_EXCEEDED`** (not `AUTO_PASS`). LOW risk profile may opt into `AUTO_PASS_WITH_WARNING`. |
| Timeout   | Cancel in-flight agents, emit `TEAM_TIMEOUT`, return partial result.              |
| Review loop overrun | Best-effort result + warning (`DEGRADED` state).                        |

### Role model

A `RoleDefinition` binds:

- A human-readable name and instructions.
- A `Capability` requirement (the `kairo.core` capability pattern from ADR-017).
- Optional tool allowlist and resource constraints.

Roles are resolved at plan time; if a step cannot be bound to any role the coordinator
fails fast in PLANNING rather than mid-execution.

### Event domain

Expert-team emits its own `TeamEventType` enum — it does NOT extend or reuse
`ExecutionEventType` or `EvolutionEventType`. All three domains publish through the
unified `KairoEventBus` facade (ADR-018).

`TeamEventType` values:

```
TEAM_STARTED, STEP_ASSIGNED, STEP_COMPLETED,
EVALUATION_STARTED, EVALUATION_RESULT, HANDOFF,
TEAM_TIMEOUT, TEAM_COMPLETED, TEAM_FAILED
```

### Resource constraints

`TeamResourceConstraint` is **independent** of the per-agent `ResourceConstraint` from
ADR-012 — it bounds the **team-level** budget (total tokens, wall-clock duration, parallel
steps, maximum feedback rounds). Per-agent constraints remain unchanged.

Rationale: a team budget that tried to reuse the per-agent shape would either
double-account tokens or force awkward aggregation semantics. A separate VO keeps both
contracts clean.

### Evaluation strategy

Pluggable via `EvaluationStrategy` SPI (see ADR-016):

- **`SimpleEvaluationStrategy`** (default, always available): rubric-driven, deterministic.
- **`AgentEvaluationStrategy`** (opt-in, v0.10.x): ReAct-style judge agent. Enabled only
  when `TeamConfig.riskProfile` explicitly triggers it, to prevent accidental cost
  escalation.

Evaluator crashes map to `REVIEW_EXCEEDED` by default. LOW risk profile may opt into
`AUTO_PASS_WITH_WARNING` — this is an explicit, auditable choice rather than a hidden
fallback.

## Five risk fixes (from plan §2)

1. **Evaluator crash default**: `REVIEW_EXCEEDED`, never silent `PASS`.
2. **Planner failure mode**: explicit (`FAIL_FAST` default, `SINGLE_STEP_FALLBACK` opt-in).
3. **Team timeout semantics**: cancel + partial result + marker event, no silent hang.
4. **Role resolution**: fail at plan time, not mid-execution.
5. **Event domain isolation**: `TeamEventType` is a peer of `ExecutionEventType` /
   `EvolutionEventType`, not a superset.

## Consequences

### Positive

- Adopters get a batteries-included expert-team workflow instead of hand-wiring
  scheduler + board + message-bus.
- `ExpertTeamCoordinator` is one of at least two `TeamCoordinator` implementations
  shipped in v0.10 (the other is `DefaultTaskDispatchCoordinator` in `kairo-multi-agent`),
  so the SPI is earned on day one.
- Failure semantics are part of the contract, not undocumented folklore.
- Compliance-sensitive users get a deterministic, auditable default path; research users
  can opt into advisory LLM strategies without changing the core contract.

### Negative

- Breaking API removal (no compat shims) forces every existing caller of the legacy
  team/task API to migrate in one wave. Acceptable under incubation-stage policy, but
  hard-breaks pre-1.0 users.
- A second starter module means two Spring Boot starters to maintain for the multi-agent
  story (`kairo-spring-boot-starter-multi-agent` + this one). Justified because expert-team
  has a distinct enablement story and dependency surface.

## Non-goals (v0.10)

- Team-level transactional durable checkpoint → v0.10.1.
- Full ReAct coordinator → v0.10.1 (SPI is ready; implementation isn't).
- Dynamic role creation → v0.11.
- Cross-team coordination → v0.11.
- Channel SPI → separate roadmap item.

## References

- ADR-012: Execution Constraint SPI (per-agent resource budget).
- ADR-014: Agent Self-Evolution Module (optional-module + separate-starter template).
- ADR-016: Coordinator SPI (contracts for this module).
- ADR-017: Agent Config Capability Pattern.
- ADR-018: Unified Event Bus (`KairoEventBus` facade).
