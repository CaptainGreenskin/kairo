# Upgrade Guide: v0.6 → v0.7

This guide covers all changes in Kairo v0.7 and the steps required to upgrade from v0.6.

## Breaking Changes

### MCP Security: Default Policy Changed to DENY_SAFE

MCP servers now default to `DENY_SAFE` — unconfigured tools are **blocked**. Previously, all tools were implicitly allowed.

**Who is affected?** Any project using `kairo-mcp` that does not explicitly configure a security policy.

**Migration options:**

**Option A (recommended):** Explicitly configure `allowedTools` for each MCP server:

```yaml
kairo:
  mcp:
    servers:
      my-server:
        securityPolicy: DENY_SAFE
        allowedTools:
          - "read_file"
          - "search"
```

**Option B:** Restore previous behavior by setting `ALLOW_ALL` (not recommended for production):

```yaml
kairo:
  mcp:
    servers:
      my-server:
        securityPolicy: ALLOW_ALL
```

**New `McpServerConfig` fields:**

| Field | Type | Default | Description |
|---|---|---|---|
| `securityPolicy` | `McpSecurityPolicy` | `DENY_SAFE` | `ALLOW_ALL`, `DENY_SAFE`, or `DENY_ALL` |
| `allowedTools` | `Set<String>` | `null` | Tools explicitly allowed under `DENY_SAFE` |
| `deniedTools` | `Set<String>` | `Set.of()` | Tools explicitly denied under `ALLOW_ALL` |
| `schemaValidation` | `boolean` | `true` | Validate tool input against JSON Schema |
| `maxConcurrentCalls` | `int` | `10` | Max concurrent tool calls per server |

`McpStaticGuardrailPolicy` runs first in the guardrail chain (`order = Integer.MIN_VALUE`), ensuring MCP security is always evaluated before custom policies.

---

## New Features

### Exception Phase B: Structured Error Fields

`KairoException` now carries structured fields for programmatic error handling:

| Field | Type | Default | Description |
|---|---|---|---|
| `errorCode` | `String` | `null` | Machine-readable error code (e.g., `"MODEL_RATE_LIMITED"`) |
| `category` | `ErrorCategory` | `null` | Error domain classification |
| `retryable` | `boolean` | `false` | Whether the operation can be retried |
| `retryAfterMs` | `Long` | `null` | Suggested retry delay in milliseconds |

**`ErrorCategory` enum values:** `MODEL`, `TOOL`, `AGENT`, `STORAGE`, `SECURITY`, `UNKNOWN`

**Migration:** No code changes required. All existing constructors still work — new fields default to `null`/`false`. Subclasses provide sensible defaults (e.g., `ModelRateLimitException` → `errorCode="MODEL_RATE_LIMITED"`, `retryable=true`).

To opt into structured fields, use the new constructors or inspect the getters:

```java
try {
    // ...
} catch (KairoException e) {
    if (e.isRetryable()) {
        long delay = e.getRetryAfterMs() != null ? e.getRetryAfterMs() : 1000;
        // schedule retry
    }
    log.error("[{}] {}: {}", e.getCategory(), e.getErrorCode(), e.getMessage());
}
```

### Guardrail SPI (@Experimental)

A new 4-phase interception framework for input/output validation:

- **Phases:** `PRE_MODEL`, `POST_MODEL`, `PRE_TOOL`, `POST_TOOL`
- **Core SPI:** `GuardrailPolicy` — implement and register as a Spring bean
- **Evaluation:** `DefaultGuardrailChain` evaluates policies in order, short-circuits on `DENY`
- **Type safety:** `GuardrailPayload` is a sealed interface with typed variants (`ModelInput`, `ModelOutput`, `ToolInput`, `ToolOutput`)

**Opt-in example:**

```java
@Component
@Order(100)
public class ContentFilter implements GuardrailPolicy {
    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (context.phase() == GuardrailPhase.PRE_MODEL
                && context.payload() instanceof GuardrailPayload.ModelInput input) {
            // inspect input, return ALLOW or DENY
        }
        return Mono.just(GuardrailDecision.allow());
    }
}
```

**No changes needed for existing code** — an empty guardrail chain is a no-op.

### Security Observability (@Experimental)

Every guardrail decision is captured as a `SecurityEvent` record for audit and compliance.

- **`SecurityEvent`** — immutable record with phase, policy name, decision, timestamp, and payload summary
- **`SecurityEventSink`** SPI — implement for custom event processing (e.g., write to a database or SIEM)
- **Default:** `LoggingSecurityEventSink` logs events at INFO level

> **Note:** OTel exporter for security events is deferred to v0.8.

### Cost Routing Extension Points (@Experimental)

Extension points for future cost-aware model routing:

- **`RoutingPolicy`** SPI — implement to influence model selection based on cost, latency, or other criteria
- **`costBudget`** field on `ModelConfig` (nullable, no enforcement in v0.7)
- **`DefaultRoutingPolicy`** — no-op placeholder

> **Note:** No routing logic is executed in v0.7. These are extension points only.

---

## ADRs

The following architectural decisions were finalized in v0.7:

- **ADR-007:** Guardrail SPI Design
- **ADR-008:** Exception Phase B Structured Fields
- **ADR-009:** MCP Security Default Policy
