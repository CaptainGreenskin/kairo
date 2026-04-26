# ADR-016: Coordinator SPI

## Status
Accepted (v0.10)

## Context

ADR-015 retires the single-choreography `TeamScheduler` API. The replacement must:

- Let adopters plug in alternative team choreographies (deterministic task dispatch,
  plan-act-evaluate, ReAct coordinator, human-in-the-loop) without modifying `kairo-core`.
- Satisfy Kairo's SPI-Earned principle: every public SPI must either have ≥2 real
  implementations at release, or be a textbook extension point that users will obviously
  want to replace. No placeholder SPIs.
- Remain stable enough that starter authors and enterprise adopters can build against it
  without bracing for churn every minor version.

An earlier draft proposed three SPIs: `TeamCoordinator`, `EvaluationStrategy`, and
`CoordinatorPolicy` (an advisory LLM step-assignment hook). The advisory-policy SPI had
zero concrete implementations in v0.10 (its only planned consumer — full ReAct
coordinator — is deferred to v0.10.1) and was a textbook case of the "SPI for a future
that hasn't landed" anti-pattern. It is **not adopted** in this ADR.

## Decision

### Two SPIs, both earned

#### 1. `TeamCoordinator`

```java
package io.kairo.api.team;

public interface TeamCoordinator {
    Mono<TeamResult> execute(TeamExecutionRequest request, Team team);
}
```

- Drives the team lifecycle end-to-end. The caller hands off a request and a bound `Team`
  and receives a `TeamResult`.
- Two implementations ship in v0.10:
  - `DefaultTaskDispatchCoordinator` in `kairo-multi-agent`: task-board dispatch semantics
    (replaces the legacy `DefaultTeamScheduler`).
  - `ExpertTeamCoordinator` in `kairo-expert-team`: plan → generate → evaluate loop.

Earned on day one.

#### 2. `EvaluationStrategy`

```java
package io.kairo.api.team;

public interface EvaluationStrategy {
    Mono<EvaluationVerdict> evaluate(EvaluationContext context);
}
```

- Decides whether a generated step artifact passes, needs revision, or escalates.
- Two implementations ship in v0.10, both in `kairo-expert-team`:
  - `SimpleEvaluationStrategy`: deterministic rubric-driven evaluator. Default.
  - `AgentEvaluationStrategy`: LLM judge agent. Opt-in via `TeamConfig.riskProfile`.
- Earned on day one.
- Evaluator is the single most obvious user-customization point (domain rubrics,
  regulated-industry validators, human-in-the-loop gates). Exposing it as an SPI costs
  one interface and unlocks the most common extension pattern.

### Rejected: `CoordinatorPolicy`

The advisory-LLM policy SPI is **not** introduced in v0.10. Rationale:

- Zero concrete implementations in v0.10 (`DefaultCoordinatorPolicy` would have been a
  no-op placeholder).
- The sole planned consumer (full ReAct coordinator) is deferred to v0.10.1.
- Under incubation-stage policy, a large breaking change in v0.10.1 to introduce the SPI
  when it is actually needed is cheaper than maintaining an empty contract through two
  releases.

When the ReAct coordinator lands, promoting `CoordinatorPolicy` from an internal interface
to a public SPI is a mechanical rename — it does not justify claiming the SPI surface now.

### SPI governance

#### Package placement

`io.kairo.api.team` owns both SPIs and their VOs. The package is part of `kairo-api` —
the only module that expert-team, multi-agent, and starter modules all depend on.

#### Value object immutability

All VOs in `io.kairo.api.team` are:

- Java `record`s or `final` classes with no setters.
- Annotated `@NullMarked` at package level (JSpecify).
- Constructor-validated (null/empty checks at boundaries).

Rationale: extensibility's cost must not be "now every extension breaks an invariant".

#### Technology Compatibility Kit (TCK)

Every SPI ships a **TCK** — a JUnit 5 abstract test class that third-party implementors
extend to validate contract compliance. Located at
`kairo-expert-team/src/testFixtures/java/io/kairo/expertteam/tck/`:

- `TeamCoordinatorTCK`: asserts lifecycle invariants (state transitions, event emission
  order, partial result on timeout, failure semantics from ADR-015).
- `EvaluationStrategyTCK`: asserts verdict invariants (non-null, score-in-range,
  crash-maps-to-REVIEW_EXCEEDED, deterministic-on-identical-input for Simple strategy).

Both TCKs are consumed by `ExpertTeamCoordinator` and the Simple/Agent strategies'
own test suites — i.e. Kairo's own implementations pass the same TCK we hand to
third parties. This is the contract test gate that matches the "可扩展" promise with
enforceable reality.

#### Versioning

- `@Experimental` for the first v0.10.0 release; stability promoted to stable once a
  third-party implementation has exercised the TCK in anger (targeted v0.11).
- Additive changes only between minor versions. Breaking changes go through an ADR.

## Consequences

### Positive

- Two SPIs, both with ≥2 implementations at release, satisfying the SPI-Earned principle
  for infrastructure-class extension points.
- TCK turns "extensible" from marketing into an enforceable contract.
- Rejecting `CoordinatorPolicy` keeps the v0.10 API surface tight and honest; the SPI can
  be introduced in v0.10.1 when there is a real caller.
- Immutable VOs plus package-level `@NullMarked` prevent extensibility from eroding
  invariants.

### Negative

- ReAct coordinator work in v0.10.1 will need a small API addition (the policy SPI).
  Under incubation-stage policy this is a feature, not a bug: the addition is guided by
  real requirements rather than speculation.
- TCK maintenance is ongoing work. Mitigation: TCK lives in the same module as the
  canonical implementations, so drift between contract and impl is visible immediately.

## Alternatives considered

### One SPI (`TeamCoordinator` only)

Rejected. Evaluation would be an internal detail of `ExpertTeamCoordinator`, but
evaluators are the single most common user-customization point — hiding them behind the
coordinator would force every domain-specific validator to re-implement the whole
coordinator. The EvaluationStrategy SPI is genuinely earned.

### Three SPIs (Coordinator + Evaluation + Policy)

Rejected — see "Rejected: `CoordinatorPolicy`" above.

### Reuse `TeamScheduler` as the SPI

Rejected. The legacy `TeamScheduler` contract is narrowly scoped to task-board dispatch
(its `dispatch(TaskBoard, List<Agent>)` method hard-codes the choreography). Generalizing
it would require signature changes that are breaking anyway — cheaper to retire it and
introduce a clean SPI.

## References

- ADR-014: Agent Self-Evolution Module (SPI-Earned precedent with three SPIs that each
  justified themselves).
- ADR-015: Expert Team Orchestration (consumer of this SPI).
- ADR-017: Agent Config Capability Pattern (role → capability binding).
- ADR-018: Unified Event Bus (event facade used by coordinators).
