# ADR-003: Cooperative Cancellation Semantics

## Status
Accepted (v0.5.1)

## Context

Kairo's ReAct loop executes tool calls reactively via Project Reactor. When a user or system
requests cancellation (e.g., timeout, user abort), the in-flight tool execution must terminate
promptly without corrupting agent state.

The initial implementation wrapped all tool execution errors — including cancellation — into
`ToolResult` error objects via `onErrorResume`. This caused cancellation to appear as a "normal
tool failure," allowing the ReAct loop to continue iterating instead of stopping immediately.

## Decision

**`AgentInterruptedException` must propagate through all pipeline stages without being caught
or converted.**

Specifically:
1. Every `onErrorResume` block in the tool execution pipeline must check for cancellation
   exceptions FIRST and re-propagate them via `Mono.error(e)`.
2. The check uses `isCancellationException()` which recognizes both `AgentInterruptedException`
   and `java.util.concurrent.CancellationException` (including in the cause chain).
3. Cancellation signals are delivered via `CancellationSignal` in Reactor Context, with
   cooperative polling every 50ms via `Flux.interval`.
4. The `withCooperativeCancellation()` operator uses `takeUntilOther` + `switchIfEmpty` to
   convert signal detection into `AgentInterruptedException`.

Pipeline cancellation flow:
```
CancellationSignal (Reactor Context)
  → IterationGuards.checkCancelled()
  → withCooperativeCancellation(Mono/Flux)
  → ToolPhase.executeToolCall() [onErrorResume: propagate if cancellation]
  → DefaultToolExecutor.executeInternal() [onErrorResume: propagate if cancellation]
  → ToolInvocationRunner (after pipeline split)
  → AgentInterruptedException bubbles to ReActLoop → agent terminates
```

## Consequences

- **Positive**: Cancellation is fast and deterministic. No "zombie" iterations after cancel.
- **Positive**: Consistent behavior regardless of which pipeline stage the tool is in when
  cancelled.
- **Negative**: Every new `onErrorResume` added to the tool pipeline must include the
  cancellation check. This is a maintenance contract that must be enforced in code review.
- **Implication for Batch 3 (ToolExecutor pipeline split)**: Each extracted component
  (`ToolInvocationRunner`, `ToolApprovalFlow`, etc.) must preserve cancellation propagation.
  The E2E cancellation test is a hard acceptance gate.

## References

- `ToolPhase.java` — `isCancellationException()` helper
- `DefaultToolExecutor.java` — `withCooperativeCancellation()`
- `IterationGuards.java` — `checkCancelled()`, `cancellationTrigger()`
