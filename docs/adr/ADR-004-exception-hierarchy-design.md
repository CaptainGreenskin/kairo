# ADR-004: Exception Hierarchy Design

## Status
Accepted (v0.6)

## Context

Kairo has two exception systems that evolved independently:

1. **kairo-api exceptions** (`KairoException` hierarchy): Public contract for consumers.
   Includes `AgentException`, `ModelException`, `ToolException` with meaningful subtypes.
2. **kairo-core exceptions** (`ModelProviderException` inner classes): Implementation-specific
   exceptions thrown by HTTP-level provider code (`RateLimitException`, `ApiException`).

This dual-track creates problems:
- External consumers must catch core-internal types, coupling them to implementation details.
- Provider exceptions bypass the public exception hierarchy, making error handling inconsistent.
- Retry logic (`ProviderRetry`) and error reporting use different type systems.

## Decision

### Phase A (v0.6) — Boundary Mapping

Apply `ExceptionMapper.toApiException()` as a reactive boundary operator (`onErrorMap`) at the
end of each provider's public methods. This maps internal exceptions to API types at a single
point per method.

**Why reactive boundary, not per-throw-site:**
- One mapping point per public method instead of per throw site — impossible to miss.
- Internal retry (`ProviderRetry.withConfigPolicy()`) is applied BEFORE the boundary map,
  so retry sees original internal types for correct classification.
- Ordering: `.transform(ProviderRetry...) .onErrorMap(ExceptionMapper::toApiException)`

**Mapping rules:**
- `ModelProviderException.RateLimitException` → `ModelRateLimitException`
- `ModelProviderException.ApiException` → `ModelApiException`
- `KairoException` subtypes → pass through (already API-layer)
- Unknown exceptions → wrapped in `KairoException`

### Phase B (v0.7) — Structured Fields

Enhance `KairoException` with machine-readable metadata:
- `error.code` — stable error code for programmatic handling
- `error.category` — classification (MODEL, TOOL, AGENT, STORAGE)
- `retryable` — boolean hint
- `retry.after.ms` — suggested delay

### Phase C (v0.8) — Deprecate Internal Exceptions

Mark `ModelProviderException` and its inner classes `@Deprecated(forRemoval = true)`.
Remove after one major version.

## Consequences

- **Positive**: External consumers only need to know `kairo-api.exception.*` types.
- **Positive**: Retry logic remains unaffected (sees original types before mapping).
- **Positive**: Incremental migration — no breaking change in Phase A.
- **Negative**: During migration, both old and new types exist. `ProviderRetry` must
  recognize both.
- **Negative**: `ExceptionMapper` adds one method-reference per provider method — minimal
  overhead but must not be forgotten in new providers.

## References

- `ExceptionMapper.java` — boundary mapping utility
- `ProviderRetry.java` — retry classification (updated to recognize both type systems)
- `docs/en/guide/exception-mapping.md` — mapping table documentation
