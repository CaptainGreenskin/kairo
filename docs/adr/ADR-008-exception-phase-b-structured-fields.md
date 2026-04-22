# ADR-008: Exception Phase B — Structured Fields

## Status
Accepted (v0.7)

## Context

Phase A (v0.6, ADR-004) established the exception hierarchy (`KairoException` →
`ModelException` / `ToolException` / `AgentException` / `MemoryStoreException`) and
reactive boundary mapping via `ExceptionMapper.toApiException()`.

For observability, cost tracking, and retry intelligence, exceptions need machine-readable
structured fields beyond the human-readable message string. Downstream systems (metrics
dashboards, alerting, retry policies) currently parse exception messages or rely on
`instanceof` checks — both are fragile.

Additionally, v0.8 `DurableExecution` will persist exceptions to store. The serialized
shape established here becomes the durable contract — field names and semantics must be
stable from v0.7 onward.

## Decision

### Structured Fields on KairoException

Add 4 structured fields to the `KairoException` base class:

| Field | Type | Description |
|-------|------|-------------|
| `errorCode` | `String` | Machine-readable code (e.g., `"MODEL_RATE_LIMITED"`, `"TOOL_PERMISSION_DENIED"`) |
| `category` | `ErrorCategory` | Classification enum |
| `retryable` | `boolean` | Retry hint for callers |
| `retryAfterMs` | `Long` | Suggested backoff delay in milliseconds (nullable) |

### ErrorCategory Enum

New enum in kairo-api with 6 values:

```java
public enum ErrorCategory {
    MODEL, TOOL, AGENT, STORAGE, SECURITY, UNKNOWN
}
```

`SECURITY` is new for Guardrail-related exceptions (ADR-007).

### Constructor Backward Compatibility

Preserve all existing 3 constructors — new fields default to `null` / `false`:
- `KairoException(String message)`
- `KairoException(String message, Throwable cause)`
- `KairoException(Throwable cause)`

Add a new builder-style constructor accepting all fields for subclass use.

### Subclass Defaults

Each subclass provides sensible defaults via the new constructor:

| Subclass | errorCode | category | retryable | retryAfterMs |
|----------|-----------|----------|-----------|--------------|
| `ModelRateLimitException` | `MODEL_RATE_LIMITED` | `MODEL` | `true` | from header |
| `ModelTimeoutException` | `MODEL_TIMEOUT` | `MODEL` | `true` | `null` |
| `ModelApiException` | `MODEL_API_ERROR` | `MODEL` | `false` | `null` |
| `ToolPermissionException` | `TOOL_PERMISSION_DENIED` | `TOOL` | `false` | `null` |
| `ToolExecutionException` | `TOOL_EXECUTION_ERROR` | `TOOL` | `false` | `null` |
| `AgentException` | `AGENT_ERROR` | `AGENT` | `false` | `null` |
| `MemoryStoreException` | `STORAGE_ERROR` | `STORAGE` | `false` | `null` |

### Serialization Compatibility

Critical for v0.8 `DurableExecution` persistence:

- JSON field names use **camelCase in both Java and JSON** — consistent with the project's
  existing Jackson defaults.
- Do NOT introduce `snake_case` in v0.7.
- Null fields omitted by default (Jackson `NON_NULL` configuration).
- The serialized shape established here becomes the durable contract for v0.8.

## Consequences

- **Positive**: `ErrorCategory` enum added to kairo-api — machine-readable classification.
- **Positive**: `KairoException` gains 4 new fields + getters for programmatic access.
- **Positive**: All 8+ subclasses enhanced with sensible default values.
- **Positive**: `ExceptionMapper` updated to populate structured fields during mapping.
- **Positive**: Zero breaking changes — existing code using old constructors continues to work.
- **Negative**: Serialized field names are now a durable contract — renaming after v0.8
  requires migration logic.

## References

- ADR-004 (Exception Hierarchy Design)
- `ExceptionMapper.java` — boundary mapping utility
- Jackson `NON_NULL` documentation
