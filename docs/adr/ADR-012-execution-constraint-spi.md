# ADR-012: ResourceConstraint SPI Design

## Status
Accepted (v0.8)

## Context

Iteration guards (maxIterations, tokenBudget, timeout) are currently hard-coded in
`IterationGuards` and `DefaultReActAgent`. This creates several problems:

- **Not extensible**: Custom constraints (e.g., cost-based exit, external kill signal,
  compliance-driven limits) cannot be plugged in without modifying core classes.
- **No graduated response**: All current guards terminate immediately. There is no way
  to warn-and-continue or gracefully wind down an execution.
- **Execution model coupling**: Different execution models (ReAct, Flow, Graph) need
  different constraints but all share the `ReActLoop`. Hard-coded guards force all models
  to use the same termination logic.

## Decision

### New ResourceConstraint SPI

Introduce an `ResourceConstraint` SPI in kairo-api (package `io.kairo.api.execution`),
marked `@Experimental`.

```java
public interface ResourceConstraint {
    Mono<ResourceValidation> validate(ResourceContext context);
    ResourceAction onViolation(ResourceValidation validation);
}
```

### Value types

**`ResourceValidation`** — immutable record:

- `violated` (boolean) — whether the constraint is violated.
- `reason` (String) — human-readable explanation.
- `metrics` (Map<String, Object>) — constraint-specific telemetry (e.g., tokens remaining,
  cost accrued).

**`ResourceAction`** — enum with four levels:

- `ALLOW` — no violation, continue execution.
- `WARN_CONTINUE` — log warning, continue execution (e.g., approaching budget limit).
- `GRACEFUL_EXIT` — finish current iteration, then stop (e.g., token budget exhausted).
- `EMERGENCY_STOP` — abort immediately, do not complete current iteration (e.g., external
  kill signal).

**`ResourceContext`** — immutable record providing constraint inputs:

- `iteration` (int) — current iteration index.
- `tokensUsed` (long) — cumulative token consumption.
- `elapsed` (Duration) — wall-clock time since execution start.
- `agentState` (AgentState) — current agent state snapshot.

### Composition semantics

Multiple constraints are evaluated in registration order. The most severe action wins:

1. First `EMERGENCY_STOP` short-circuits — remaining constraints are not evaluated.
2. `GRACEFUL_EXIT` wins over `WARN_CONTINUE`.
3. `WARN_CONTINUE` wins over `ALLOW`.
4. `ALLOW` is the default when no constraint is violated.

All `ResourceValidation` results are collected (even after short-circuit) for
observability. Violations are emitted as structured log events.

### Integration with ReActLoop

`DefaultResourceConstraint` consolidates existing `IterationGuards` checks into the
new SPI:

- `maxIterations` → `MaxIterationConstraint` (returns `GRACEFUL_EXIT`)
- `tokenBudget` → `TokenBudgetConstraint` (returns `GRACEFUL_EXIT`)
- `timeout` → `TimeoutConstraint` (returns `EMERGENCY_STOP`)

The constraint chain is injected into `ReActLoop` via constructor. `IterationGuards.evaluate()`
delegates to the `ResourceConstraint` chain internally — existing call sites are unaffected.

### Migration path

Existing `maxIterations`, `tokenBudget`, and `timeout` configuration properties continue
to work unchanged. `DefaultResourceConstraint` reads them from `AgentConfig` and creates
the corresponding built-in constraints.

Custom constraints are pluggable via Spring `@Bean` registration:

```java
@Bean
public ResourceConstraint costLimitConstraint() {
    return new CostLimitConstraint(maxCostDollars);
}
```

All registered `ResourceConstraint` beans are auto-discovered and added to the chain
after the built-in constraints.

## Consequences

### Positive

- Custom termination logic is pluggable without modifying core classes.
- Graduated response (WARN → GRACEFUL_EXIT → EMERGENCY_STOP) replaces binary stop/continue.
- Built-in constraints are now individually testable — each is a standalone class.
- `ResourceValidation.metrics` provides structured observability data for dashboards.
- Existing configuration is fully backward-compatible.

### Trade-offs

- Constraint evaluation adds per-iteration overhead — mitigated by short-circuit semantics
  and the typical small number of constraints (< 10).
- `ResourceAction` ordering is implicit (enum ordinal) — must be documented clearly to
  avoid misuse by custom constraint authors.
- `DefaultResourceConstraint` is a wrapper that delegates to the old `IterationGuards`
  logic — temporary duplication until `IterationGuards` is fully deprecated.

## References

- ADR-007 (GuardrailPolicy chain pattern as composition precedent)
- `IterationGuards` in kairo-core (current implementation)
- `ReActLoop` in kairo-core
- `DefaultReActAgent` in kairo-core
