# ADR-007: Guardrail SPI Design

## Status
Accepted (v0.7)

## Context

Kairo needs a request/response interception mechanism for safety concerns including
content filtering, prompt injection detection, PII filtering, and output validation.

The existing `PermissionGuard` SPI is tool-scoped — it checks tool name + input only.
It cannot intercept model requests/responses or tool outputs. Guardrails need to operate
at four distinct boundary points: PRE_MODEL, POST_MODEL, PRE_TOOL, and POST_TOOL.

Overloading `PermissionGuard` with model-level interception would violate its single
responsibility and bloat its contract with unrelated concerns.

## Decision

### New GuardrailPolicy SPI

Introduce a new `GuardrailPolicy` SPI in kairo-api (package `io.kairo.api.guardrail`),
marked `@Experimental`, completely separate from `PermissionGuard`.

**Core types (6 total):**

1. **`GuardrailPolicy`** — SPI interface with `evaluate(GuardrailContext): Mono<GuardrailDecision>`
2. **`GuardrailContext`** — record carrying the typed payload, agent identity, and metadata map
3. **`GuardrailPayload`** — sealed interface with 4 variants:
   - `ModelInput` — messages about to be sent to the model
   - `ModelOutput` — messages returned from the model
   - `ToolInput` — tool name + arguments before execution
   - `ToolOutput` — tool result after execution
4. **`GuardrailDecision`** — record with `Action` enum and optional reason/modified payload
5. **`GuardrailDecision.Action`** — enum: `ALLOW`, `DENY`, `MODIFY`, `WARN`
6. **`DefaultGuardrailChain`** — ordered chain evaluator (in kairo-core)

`GuardrailPayload` uses `List<Msg>` (the project's existing message type), NOT a new
Message type — no unnecessary abstraction.

### Chain Evaluation Semantics

`DefaultGuardrailChain` evaluates policies in registration order:
- Short-circuits on `DENY` — remaining policies are not evaluated.
- Merges `MODIFY` payloads sequentially — each policy sees the modified output of the previous.
- `WARN` is recorded but does not halt the chain.
- Empty chain returns `ALLOW` (no-op) — zero overhead when no policies are registered.

### Tool Pipeline Ordering

The full tool execution pipeline with guardrail placement:

```
CircuitBreaker → ActiveToolConstraints → PlanMode → PermissionGuard → Guardrail(PRE_TOOL) → Execution → Guardrail(POST_TOOL) → Sanitize
```

Key invariants:
- `PermissionGuard` rejects BEFORE `Guardrail` — consistent with "static first, dynamic second" principle.
- A `PermissionGuard` denial does NOT trigger Guardrail evaluation.

### MCP Integration

MCP static policy (allow/deny lists) is implemented as `McpStaticGuardrailPolicy` with
`order = Integer.MIN_VALUE` within the same `DefaultGuardrailChain` — single implementation
point, single audit trail. See ADR-009 for full MCP security design.

## Consequences

- **Positive**: New package `io.kairo.api.guardrail` with 6 well-scoped types.
- **Positive**: `DefaultGuardrailChain` in kairo-core provides ordered, short-circuit evaluation.
- **Positive**: `GuardrailChain` injected into `ReActLoop` (model boundaries) and
  `DefaultToolExecutor` (tool boundaries) — all four interception points covered.
- **Positive**: Empty chain is zero-overhead — no performance impact for users who don't register policies.
- **Negative**: All v0.7 guardrail types are `@Experimental` — contract may change in v0.8.
- **Negative**: Adds complexity to the tool pipeline ordering — must be carefully documented.

## References

- ADR-004 (Exception Hierarchy Design)
- `PermissionGuard` SPI in kairo-api
- OWASP Agentic AI Top 10

## Spring Boot Configuration

Example `application.yml` registering guardrail policies:

```yaml
kairo:
  guardrail:
    policies:
      - name: content-filter
        phase: PRE_MODEL
        order: 0
      - name: pii-redactor
        phase: POST_MODEL
        order: 10
      - name: tool-audit
        phase: PRE_TOOL
        order: 0
```
