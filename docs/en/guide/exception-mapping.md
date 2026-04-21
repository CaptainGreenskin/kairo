# Exception Mapping Reference

Kairo maintains a clean separation between internal implementation exceptions and the public API exception hierarchy.
All exceptions surfaced to consumers extend `io.kairo.api.exception.KairoException`.

## Exception Hierarchy

```
KairoException (kairo-api)
├── AgentException
│   ├── AgentExecutionException
│   └── AgentInterruptedException
├── ModelException
│   ├── ModelApiException          (NEW in v0.6)
│   ├── ModelRateLimitException
│   ├── ModelTimeoutException
│   └── ModelUnavailableException
│       └── CircuitBreakerOpenException
├── ToolException
│   ├── ToolPermissionException
│   └── PlanModeViolationException
└── MemoryStoreException           (NEW in v0.6)
```

## Internal-to-Public Mapping Table

| Internal Exception (kairo-core) | Public Exception (kairo-api) | Retryable | Context |
|---|---|---|---|
| `ModelProviderException.RateLimitException` | `ModelRateLimitException` | Yes | HTTP 429, rate limit reached |
| `ModelProviderException.ApiException` | `ModelApiException` | No (unless transient) | Non-200 HTTP status, response parse failure |
| `CircuitBreakerOpenException` | `ModelUnavailableException` | Yes (after cooldown) | Circuit breaker open state |
| `JdbcMemoryStore` storage errors | `MemoryStoreException` | No | SQL/connection failure |
| `LoopDetectionException` | `AgentExecutionException` | No | Infinite tool-call loop detected |

## Mapping Strategy

Exception mapping is applied at **reactive boundaries** using `onErrorMap`, not at individual throw sites.
This ensures:
- Internal retry logic (`ProviderRetry`) sees original exception types for correct classification
- Only final, un-retried errors are mapped to public API types
- One mapping point per public method, impossible to miss a throw site

```java
// Pattern used in all providers:
return Mono.defer(() -> { /* internal logic throwing internal exceptions */ })
    .transform(ProviderRetry.withConfigPolicy(...))    // retry sees original types
    .onErrorMap(ExceptionMapper::toApiException);       // boundary mapping
```

## Structured Fields (Planned for Phase B)

In a future release, `KairoException` will be enhanced with structured fields:
- `error.code` — machine-readable error code
- `error.category` — error category classification
- `retryable` — whether the operation can be retried
- `retry.after.ms` — suggested retry delay in milliseconds
