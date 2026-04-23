# ADR-013: Cost-Aware Routing Design

## Status
Accepted (v0.8)

## Context

Kairo has no cost-based model selection. Users pay premium-tier prices for simple tasks
(e.g., summarization routed to GPT-4o when GPT-4o-mini would suffice). The `RoutingPolicy`
SPI exists (introduced in v0.7, marked `@Experimental`) but has no cost-aware implementation.

Without cost routing:

- Simple tasks consume expensive model capacity unnecessarily.
- Users have no way to express cost budgets at the agent or task level.
- Provider fallback on error requires manual configuration per deployment.

## Decision

### Tier registry

Introduce `ModelTier` as a configuration record:

```java
public record ModelTier(
    String tierName,
    Set<String> models,
    BigDecimal costPerInputToken,
    BigDecimal costPerOutputToken,
    Duration expectedLatency
) {}
```

`ModelTierRegistry` holds the configurable model-to-tier mapping, populated from Spring
properties at startup. Tiers are ordered by cost (cheapest first).

### CostAwareRoutingPolicy

`CostAwareRoutingPolicy` implements `RoutingPolicy` in kairo-core. It **extends** the
routing system — it does NOT replace `DefaultRoutingPolicy`.

Selection logic:

1. Estimate request cost: message token count × tier pricing (uses the same char/4
   heuristic as `ToolResultBudget` — see ADR-010).
2. Select the cheapest tier whose estimated cost fits within the configured `costBudget`.
3. If the selected tier's provider returns an error or times out, fall back to the next
   cheapest tier.

### Fallback chain semantics

- Tiers are ordered from cheapest to most expensive.
- Each tier is tried in order. On provider error (non-2xx, timeout, circuit-breaker open),
  move to the next tier.
- If all tiers are exhausted, propagate the last error to the caller.
- Fallback is linear — no retry backoff within a tier in v0.8. Per-tier retry is deferred
  to v0.9.

### Spring configuration

```yaml
kairo:
  routing:
    model-tiers:
      - tier-name: economy
        models: [gpt-4o-mini, claude-3-haiku]
        cost-per-input-token: 0.00000015
        cost-per-output-token: 0.0000006
      - tier-name: standard
        models: [gpt-4o, claude-3.5-sonnet]
        cost-per-input-token: 0.0000025
        cost-per-output-token: 0.00001
    fallback-chain: [economy, standard]
```

Tier names are user-defined strings. The `fallback-chain` list defines evaluation order
explicitly — it is NOT derived from cost sorting. This allows operators to skip tiers
or reorder based on deployment-specific preferences.

### Cost estimation accuracy

Definition of Done for cost estimation:

- **Pre-release gate**: Estimated cost must be within 20% of a mock billing oracle across
  the standard test suite.
- **Post-release gate (v0.8.1)**: Estimated cost must be within 15% of actual provider
  billing after a 7-day production soak test.

If post-release accuracy exceeds the 15% threshold, the char/4 heuristic will be replaced
with a tokenizer-based estimate in v0.9.

## Consequences

### Positive

- Simple tasks are automatically routed to cheaper models, reducing operational cost.
- Fallback chain provides resilience — provider outages are handled without manual intervention.
- Tier registry is configuration-driven — no code changes needed to add new models or
  adjust pricing.
- Extends existing `RoutingPolicy` SPI — no new abstraction, consistent with v0.7 design.
- Cost estimation reuses the char/4 heuristic from `ToolResultBudget` — no new dependency.

### Trade-offs

- Char/4 token estimation is approximate — actual costs may deviate up to 20% from estimates.
  Acceptable for v0.8, with tokenizer-based estimation planned for v0.9.
- Linear fallback without per-tier retry may cause unnecessary tier escalation on transient
  errors — deferred to v0.9.
- Tier pricing is static configuration — does not track real-time provider pricing changes.
  Operators must update configuration when providers change pricing.
- `CostAwareRoutingPolicy` adds a dependency on `ModelTierRegistry` — increases configuration
  surface area for deployments that don't need cost routing.

## References

- `RoutingPolicy` SPI in kairo-api (v0.7, `@Experimental`)
- `DefaultRoutingPolicy` in kairo-core
- ADR-010 (char/4 token estimation precedent)
