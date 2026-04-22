# ADR-006: Compaction Pipeline Architecture

## Status

Accepted (v0.5.0)

## Context

Agent memory grows unbounded in long-running sessions. A single conversation can accumulate
thousands of context entries, eventually exceeding model context windows and degrading response
quality. Academic research (Morph FlashCompact) demonstrates that multi-stage compression
achieves significantly better retention (37% loss) compared to single-pass approaches (98%
loss), validating a layered strategy.

The initial approach of truncating old messages was too aggressive — important context from
early in the conversation was lost, causing the agent to repeat questions or forget constraints
the user had already provided.

## Decision

Implement a 6-stage layered compaction pipeline with pressure-based triggers:

1. **Snip** — Remove tool call/result pairs that are no longer referenced. Lightest operation,
   no LLM involvement. Triggered at 80% memory pressure.
2. **Micro** — Summarize individual tool results into one-line summaries. Lightweight LLM call
   per result. Triggered at 84% memory pressure.
3. **Collapse** — Merge consecutive same-role messages into single messages. No LLM involvement.
   Triggered at 88% memory pressure.
4. **Auto** — LLM-generated summary of a sliding window of messages, replacing the window with
   a summary message. Triggered at 92% memory pressure.
5. **Partial** — Summarize everything except the last N messages (configurable). Aggressive LLM
   summarization. Triggered at 95% memory pressure.
6. **Full** — Emergency compaction: summarize the entire conversation into a single context
   message. Only triggered at 98% memory pressure as a last resort.

**Pressure-based triggers** start at 80% of the configured token budget. Each stage is
progressively more aggressive. The pipeline short-circuits when pressure drops below the
threshold for the next stage.

**Circuit breaker** protects against LLM-backed compaction failures (stages 2, 4, 5, 6).
After 3 consecutive failures, the circuit opens and the pipeline falls back to non-LLM stages
only. The circuit resets after a configurable cooldown period.

All thresholds are configurable via the `CompactionThresholds` record (9 fields):
`snipThreshold`, `microThreshold`, `collapseThreshold`, `autoThreshold`, `partialThreshold`,
`fullThreshold`, `retainCount`, `circuitBreakerFailures`, `circuitBreakerCooldown`.

## Consequences

- **Positive**: Memory pressure is handled gracefully with proportional response — light
  operations first, heavy operations only when necessary.
- **Positive**: Each stage is independently configurable and testable. Operators can tune
  thresholds per use case (e.g., customer support may want higher `retainCount`).
- **Positive**: The 80% trigger threshold is validated by industry research (Morph FlashCompact)
  as the optimal point to begin compaction before quality degradation.
- **Positive**: Circuit breaker prevents cascading failures when the LLM is unavailable —
  the agent continues operating with reduced but functional context management.
- **Negative**: 6 stages add complexity. Operators must understand the pipeline to tune it
  effectively. Mitigated by sensible defaults in `CompactionThresholds`.
- **Negative**: LLM-backed stages (Micro, Auto, Partial, Full) incur additional API costs.
  The pressure-based triggering minimizes unnecessary calls.

## References

- `CompactionStrategy.java` — SPI for compaction stages
- `CompactionConfig.java` — Threshold configuration
- `CompactionResult.java` — Stage execution results
- Morph FlashCompact research — Multi-stage compression validation
