# IterationSignal Migration Guide (M131)

## 1. Overview

M131 replaces the opaque `Supplier<Mono<Msg>>` continuation thunks with a **sealed `IterationSignal` interface**. Each phase now returns a typed signal declaring what happened, and the loop orchestrator (`ReActLoop.dispatchSignal`) is the sole interpreter.

**Motivation:** Control flow was implicit — hidden inside "call or don't call the thunk." This led to silent iteration stalls (M127/M128 bugs) where a forgotten continuation call caused the agent to hang with no error, no log, and no timeout.

## 2. Old vs New Control Flow

### Old model (pre-M131)

```
ReasoningPhase.execute(cfg, loopContinuation) → Mono<Msg>
ToolPhase.executeAndContinue(calls, loopContinuation) → Mono<Msg>
```

- Each phase internally decides whether to call `loopContinuation.get()`.
- Control flow scattered across 8+ methods, invisible to the loop orchestrator.
- Bugs manifest as silent hangs — no signal, no error.

### New model (M131+)

```
ReasoningPhase.execute(cfg) → Mono<IterationSignal>
ToolPhase.execute(calls) → Mono<IterationSignal>
ReActLoop.dispatchSignal(sig) → interprets signal, decides next action
```

- Control flow is **explicit**, **centralized**, and **compile-time exhaustive**.
- A missing case is a compile error, not a silent hang.

## 3. Signal Case Mapping

| Signal | Meaning | Counter increment? |
|--------|---------|-------------------|
| `Complete(Msg)` | Final answer produced | No (terminal) |
| `ToolCallsRequested(List)` | Model wants to call tools | No (half-iter) |
| `ContinueAfterTools(int)` | Tools executed, continue reasoning | Yes |
| `ContinueWithNudge(Msg, String)` | Strategy nudge, continue | Yes |
| `CompactThenContinue(String)` | Budget exceeded, compact first | Yes |
| `Skip(String)` | No-op (hook veto / rescue / warn) | No (not a real iter) |
| `LoopDetected(LoopDetectionInfo)` | Loop hard-stop | No (terminal) |
| `Abort(Throwable, String)` | Unrecoverable error | No (terminal) |

## 4. Phase Responsibility Boundaries

| Component | Input | Output | Constraints |
|-----------|-------|--------|-------------|
| **ReasoningPhase** | `ModelConfig` | `IterationSignal` | No ToolPhase reference, no recursion. Pure-functional, testable in isolation. |
| **ToolPhase** | Tool calls list | `IterationSignal` | No loop detection, no recursion. Executes tools and reports result. |
| **ReActLoop.dispatchSignal** | `IterationSignal` | `Mono<Msg>` | Sole recursion point. Sole iteration counter authority. Sole loop guard host. |

## 5. Iteration Counter Semantics

The counter increments **only** when the model has been called and the loop decides the next action:

- **Increments:** `ContinueAfterTools`, `ContinueWithNudge`, `CompactThenContinue`
- **Does NOT increment:**
  - `ToolCallsRequested` — first half of same iteration (waiting for tool result)
  - `Skip` — model was not called, should not consume max-iter budget
  - Terminal signals (`Complete`, `Abort`, `LoopDetected`) — loop is ending

**Rule:** "Iter increments when model has been called once and ReAct decides next action."

## 6. Max-Consecutive-Skip Guard

```
Environment variable: KAIRO_AGENT_MAX_CONSECUTIVE_SKIPS (default: 5)
```

- Each `Skip` signal increments a `consecutiveSkips` counter.
- Any non-Skip signal **resets** the counter to zero.
- When `consecutiveSkips > threshold` → emit `Abort` with descriptive error.

**Prevents:** pre-reasoning hook infinite veto loops, loop rescue deadlock, misconfigured strategy causing permanent no-ops.

## 7. Two-Level Dispatch

```
Level 1:  driveLoop()
            → reasoningPhase.execute(cfg)
            → signal
            → dispatchSignal(sig)

Level 2:  case ToolCallsRequested
            → loop guard check
            → toolPhase.execute(calls)
            → sub-signal
            → re-enters dispatchSignal(subSig)
```

All recursion uses `Mono.defer(this::runLoop)` — fully stack-safe under Project Reactor's trampoline.

## 8. Backward Compatibility

| Layer | Impact |
|-------|--------|
| `Agent.call()` | Unchanged — user-facing API stable |
| `AgentContinuationStrategy` SPI | Unchanged — still returns `ContinuationDecision` |
| Strategy → signal mapping | `Pass`→`Complete`, `Terminate`→`Complete`, `Nudge`→`ContinueWithNudge`, `CompactAndRetry`→`CompactThenContinue`, `Escalate`→`Abort` |
| kairo-code | Zero source changes required (SNAPSHOT bump only) |

The `ContinuationDecision` enum (`Pass`/`Terminate`/`Nudge`/`CompactAndRetry`/`Escalate`) is still consumed by `ReasoningPhase` internally and mapped to the appropriate signal before returning.

## 9. Observability

Every signal emission produces structured logs:

```
INFO  react.signal iter={} signal={} reason={}
INFO  react.skip  iter={} consecutive={} reason={}
```

- **`react.signal`** — emitted for every signal; one grep reconstructs the full iteration story.
- **`react.skip`** — emitted specifically for Skip paths with consecutive count for alerting.
- The OTel iteration span (introduced in M126) carries the signal type as a span attribute.
