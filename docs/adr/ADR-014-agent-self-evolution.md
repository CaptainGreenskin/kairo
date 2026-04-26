# ADR-014: Agent Self-Evolution Module and Governance

## Status
Accepted (v0.9)

## Context

Tencent XuanWu Lab discovered that Hermes Agent autonomously self-creates security review
skills under repeated adversarial attacks — a concrete example of emergent agent self-evolution
in production. The Java ecosystem currently has no framework for governing this behavior:
agents either cannot evolve, or they evolve without oversight.

Kairo needs a self-evolution capability as a differentiation point:

- Agents should be able to extract reusable skills from successful execution traces.
- Evolved skills must pass through a governance pipeline before activation — ungoverned
  evolution is a security liability.
- The evolution subsystem must be truly optional: enterprises that do not want self-evolution
  must be able to exclude it entirely from the classpath.

## Decision

### A+C hybrid model

We evaluated three architectural options:

- **(A) Modular pluggable**: Evolution as an independent module, wired via SPI. Core runtime
  has zero knowledge of evolution.
- **(B) Kernel-embedded**: Evolution logic inside kairo-core, behind feature flags.
- **(C) Tool-driven**: Evolution capabilities exposed as agent tools (extract-skill,
  activate-skill, etc.).

Decision: **A+C hybrid** — evolution is a separate module (A) that exposes its capabilities
as agent tools (C). The module is wired into the agent via hook and prompt injection, not
via compile-time coupling.

### Module structure

- `kairo-evolution`: Independent module containing the evolution engine, governance pipeline,
  and state management. Depends only on `kairo-api` — **kairo-core has ZERO dependency on
  this module**.
- `kairo-spring-boot-starter-evolution`: Standalone Spring Boot starter that auto-configures
  the evolution subsystem. This is a separate starter (not aggregated into
  `kairo-spring-boot-starter`).

### Hook and prompt injection

Evolution integrates with the agent through two extension points defined in `kairo-api`:

- `AgentBuilderCustomizer`: The starter provides a customizer bean that wires
  `EvolutionHook` (a `PostActingHook` implementation) into the agent's acting pipeline.
- `SystemPromptContributor`: The starter provides a contributor bean via
  `SkillContentInjector` that appends evolved skill descriptions to the agent's system
  prompt at execution time.

This means kairo-core never imports or references any evolution class. The starter is the
only place where evolution meets core.

### SPI surface (SPI Earned principle)

Three SPIs, each satisfying the four objective conditions from the project design principles:

1. **`EvolutionPolicy`**: Decides whether a given execution trace should trigger skill
   extraction. Default: `ThresholdEvolutionPolicy` (success count ≥ N).
2. **`EvolvedSkillStore`**: Persists evolved skill state (quarantined, active, suspended).
   Default: `InMemoryEvolvedSkillStore` (v0.9).
3. **`EvolutionTrigger`**: Defines when evolution evaluation runs (post-execution, scheduled,
   manual). Default: `PostExecutionEvolutionTrigger`.

Six value objects define the evolution domain model:

- `EvolvedSkill`, `SkillExtractionResult`, `EvolutionEvaluation`
- `SkillGovernanceResult`, `EvolutionState`, `EvolutionMetrics`

### Event domain separation

Evolution defines its own `EvolutionEventType` enum — it does NOT extend or reuse
`ExecutionEventType`. This keeps the core event domain clean and prevents evolution concerns
from leaking into the execution event bus.

### Governance pipeline

Evolved skills pass through a three-stage governance pipeline before activation:

1. **Quarantine**: Newly extracted skills are placed in quarantine. They are visible for
   inspection but cannot be used by agents.
2. **Scan**: Quarantined skills are evaluated against configured policies (content scanning,
   safety checks, capability validation).
3. **Activate**: Skills that pass scanning are promoted to active status and become available
   for agent use via prompt injection.

### State machine with SUSPENDED

The skill lifecycle state machine includes a `SUSPENDED` state to prevent runaway
self-healing:

```
QUARANTINED → SCANNING → ACTIVE → SUSPENDED
                ↓                      ↓
             REJECTED              QUARANTINED (re-evaluation)
```

If an active skill causes repeated failures, it transitions to `SUSPENDED` — not back to
`ACTIVE`. Manual intervention (or a configured policy) is required to move a suspended skill
back to `QUARANTINED` for re-evaluation. This circuit-breaker prevents infinite
evolve-fail-evolve loops.

## Why NOT kernel-embedded (Option B)

Even with feature flags, the kernel-embedded approach has fundamental problems:

1. **Classpath pollution**: Evolution classes remain on the main classpath regardless of
   whether the feature flag is enabled. This violates the modular pluggable principle —
   enterprises cannot physically exclude evolution code.
2. **Risk surface**: Evolution logic (skill extraction, governance state machines) stays in
   the critical execution path. A bug in evolution code can affect core agent execution even
   when the feature is "disabled."
3. **Testing surface**: Core module tests must account for evolution feature flag combinations,
   increasing the test matrix.
4. **Dependency creep**: Once evolution code lives in core, it inevitably accumulates
   dependencies on core internals, making future extraction harder.

The modular approach (Option A) eliminates all four concerns by construction.

## v0.9 Limitations

- **InMemory state store**: `InMemoryEvolvedSkillStore` loses all evolved skill state on
  restart. This is a known trade-off for fast validation — the evolution subsystem can be
  fully exercised without requiring a database.
- **No content scanning**: The governance pipeline's scan stage accepts all skills by default.
  Real content scanning (`SkillContentScanningPolicy`) is deferred to v0.10.
- **No self-patch**: The `PostActingHook` observes execution results but cannot modify the
  agent's tool set at runtime. Self-patching (hot-swapping tools) is deferred to v0.10.

## v0.10 Path

- **JDBC state store**: `JdbcEvolvedSkillStore` for persistent evolved skill state across
  restarts, following the same Flyway migration pattern as `JdbcDurableExecutionStore`.
- **Content scanning**: `SkillContentScanningPolicy` SPI implementation with configurable
  rule sets (regex patterns, LLM-based safety evaluation).
- **Self-Patch via PostActing hook**: Allow the evolution hook to register new tools into
  the agent's tool set at runtime, enabling true self-modification.
- **Metrics export**: Evolution metrics (extraction rate, activation rate, suspension rate)
  exported via the existing observability infrastructure.

## Consequences

### Positive

- Evolution is truly optional — excluding `kairo-spring-boot-starter-evolution` from the
  classpath completely removes all evolution code and behavior.
- Enterprise deployments can exclude evolution entirely without code changes.
- The governance pipeline provides a safety net — no evolved skill reaches production without
  passing through quarantine and scanning.
- Event domain separation keeps the core event bus clean.
- SPI surface is minimal (3 SPIs) — follows SPI Earned principle.

### Trade-offs

- Initial integration requires an explicit starter dependency — evolution is not included
  in the default `kairo-spring-boot-starter`.
- `InMemoryEvolvedSkillStore` in v0.9 means evolved skills are lost on restart — acceptable
  for validation, not for production use.
- The A+C hybrid adds indirection: understanding the full evolution flow requires tracing
  through hook injection, prompt contribution, and tool invocation.
- Standalone starter increases the number of Maven artifacts consumers must manage.

## References

- `AgentBuilderCustomizer` SPI in kairo-api
- `SystemPromptContributor` SPI in kairo-api
- `PostActingHook` in kairo-api
- Tencent XuanWu Lab Hermes Agent self-evolution discovery
- ADR-005 (SPI Earned principle)
