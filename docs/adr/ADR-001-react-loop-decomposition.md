# ADR-001: ReAct Loop Decomposition

## Status

Accepted (v0.5.1)

## Context

The `ReActLoop` class had grown to 1,008 lines and was responsible for four distinct concerns:
iteration control, model calling (reasoning), tool execution (acting), and hook routing. This
concentration of responsibilities made the class difficult to test in isolation, hard to extend
with new phases, and prone to merge conflicts when multiple contributors worked on different
aspects of the loop.

Unit tests for individual concerns (e.g., testing only the tool execution timeout behavior)
required setting up the entire ReAct orchestration, leading to brittle and slow tests.

## Decision

Decompose `ReActLoop` into four focused phase components, with `ReActLoop` retained as a thin
orchestrator (~263 lines):

1. **`IterationGuards`** — Enforces iteration limits, token budget checks, and cooperative
   cancellation polling. Extracted from the loop's pre-iteration checks.
2. **`ReasoningPhase`** — Handles model invocation (call/stream), response parsing, and
   content-block assembly. Extracted from the "think" portion of the loop.
3. **`ToolPhase`** — Manages tool dispatch, parallel execution, timeout enforcement, and
   result aggregation. Extracted from the "act" portion of the loop.
4. **`HookDecisionApplier`** — Evaluates hook results (continue, stop, override) and applies
   them to control the loop flow. Extracted from the hook routing logic.

`ReActLoop` becomes a thin orchestrator that calls each phase in sequence per iteration:
`IterationGuards → ReasoningPhase → HookDecisionApplier → ToolPhase → HookDecisionApplier`.

## Consequences

- **Positive**: Each phase is independently testable with focused unit tests. `ToolPhase` tests
  no longer require a real `ModelProvider`.
- **Positive**: New phases (e.g., a planning phase, a reflection phase) can be added without
  modifying the orchestrator — just insert into the phase sequence.
- **Positive**: All 1,211 existing tests passed without modification after the decomposition,
  confirming behavioral equivalence.
- **Negative**: Four additional classes increase the file count. Developers must understand the
  phase contract to work on the loop.
- **Negative**: Cross-phase concerns (e.g., cancellation propagation) must be carefully
  maintained across all phase boundaries. See ADR-003 for the cancellation contract.

## References

- `ReActLoop.java` — Thin orchestrator (~263 lines post-decomposition)
- `IterationGuards.java` — Iteration limits, token budget, cancellation polling
- `ReasoningPhase.java` — Model invocation and response parsing
- `ToolPhase.java` — Tool dispatch, parallel execution, timeout
- `HookDecisionApplier.java` — Hook evaluation and flow control
