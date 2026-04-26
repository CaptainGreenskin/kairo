# ADR-005: Provider Decomposition Template

## Status

Accepted (v0.6)

## Context

`OpenAIProvider` (904 lines) and `AnthropicProvider` (1,052 lines) each contained five
interleaved concerns: request building, response parsing, SSE streaming, error classification,
and retry integration. This monolithic structure caused several problems:

1. **Testing difficulty**: Testing SSE reconnection logic required mocking the entire provider,
   including unrelated request-building code.
2. **Bug isolation**: An SSE parsing bug in `AnthropicProvider` was initially misdiagnosed as
   a request-building issue because both concerns lived in the same class.
3. **Code duplication**: Both providers independently implemented nearly identical SSE
   subscriber logic and error classification patterns.
4. **Onboarding friction**: Adding a new provider (e.g., Google Gemini) required understanding
   and replicating all five concerns from scratch.

## Decision

Decompose each provider into a 5-piece template:

1. **`RequestBuilder`** — Transforms Kairo's `ModelConfig` + messages into the provider's
   native HTTP request format. Pure function, no I/O.
2. **`ResponseParser`** — Converts the provider's response JSON into Kairo's `ModelResponse`.
   Pure function, no I/O.
3. **`SseSubscriber`** — Manages the SSE connection lifecycle: connection, reconnection,
   backpressure, and partial-event assembly. Reactive I/O only.
4. **`ErrorClassifier`** — Maps HTTP status codes and provider-specific error bodies into
   Kairo's exception hierarchy (retryable vs. fatal). Pure function.
5. **`ProviderFacade`** — Thin orchestrator that wires the above four components together
   and exposes the `ModelProvider` SPI contract.

**Reuse existing `ReactiveRetryPolicy`** — no new retry abstraction is introduced. The
`ProviderFacade` delegates retry decisions to the existing policy, using `ErrorClassifier`
output to determine retryability.

The pattern was piloted on `OpenAIProvider` first, then applied to `AnthropicProvider` using
the same template.

## Consequences

- **Positive**: Each concern is independently testable. `SseSubscriber` tests don't need
  request-building setup.
- **Positive**: New providers follow a clear template: implement 4 components + 1 facade.
  Estimated effort for a new provider drops from ~3 days to ~1 day.
- **Positive**: SSE subscriber bugs are isolated from request building — no more misdiagnosis.
- **Positive**: No new retry abstraction — reuses `ReactiveRetryPolicy`, keeping the
  abstraction count stable.
- **Negative**: Five classes per provider increases file count (2 files → 10 files for two
  providers). Mitigated by clear naming convention: `OpenAiRequestBuilder`,
  `OpenAiResponseParser`, etc.
- **Negative**: Cross-cutting changes (e.g., adding a new header to all requests) now require
  touching `RequestBuilder` in each provider.

## References

- `ReactiveRetryPolicy.java` — Existing retry policy reused by all providers
- `ModelProvider.java` — SPI contract implemented by `ProviderFacade`
- ADR-001 — Similar decomposition pattern applied to `ReActLoop`
