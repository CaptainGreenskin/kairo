# ModelProvider — API Reference

**Package:** `io.kairo.api.model`
**Stability:** `@Stable(since = "1.0.0")` — "Core model invocation contract; unchanged since v0.1"
**Canonical source:** [`ModelProvider.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/model/ModelProvider.java)

Abstraction over an LLM backend. Implementations wrap the provider's REST / streaming API
and adapt it to Kairo's reactive `Mono<ModelResponse>` / `Flux<ModelResponse>` contract.
Providers are decomposed into a four-stage [`ProviderPipeline`](./ProviderPipeline.md) for
advanced customization (request builder / response parser / stream subscriber / error classifier).

## Surface

```java
public interface ModelProvider {
    Mono<ModelResponse> call(List<Msg> messages, ModelConfig config);
    Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config);
    String name();
}
```

## Stability Guarantees

- Binary compatibility held across v1.x.
- New `default` methods may be added.
- `call` / `stream` / `name` signatures are frozen for the major.

## Default Implementations

| Impl | Module | Notes |
|------|--------|-------|
| `AnthropicProvider` | `kairo-core` | Claude / Messages API. Implements `ProviderPipeline`. |
| `OpenAIProvider` | `kairo-core` | Chat Completions API. Implements `ProviderPipeline`. |
| `MockModelProvider` | `kairo-examples` | Deterministic test double. |

## Usage Example

```java
ModelProvider provider = new AnthropicProvider(System.getenv("ANTHROPIC_API_KEY"));
ModelResponse resp = provider.call(
    List.of(Msg.user("Hello")),
    ModelConfig.builder().model("claude-opus-4-7").build())
    .block();
```

## Configuration

Model selection, tokens, temperature, tool definitions live on `ModelConfig`.
Provider-specific knobs (base URL, timeouts, retries) live on the provider's builder.

## Lifecycle

1. One provider instance per backend per application (builders create them).
2. Thread-safe; invoked concurrently across many requests.
3. `stream()` returns a cold `Flux` — each subscriber initiates a fresh request.

## Migration Policy

`@Stable` surface; breaking changes require ADR + japicmp approval and must land in a major.

## Related

- ADR: [ADR-005 — Provider decomposition template](../../adr/ADR-005-provider-decomposition-template.md)
- SPI: [ProviderPipeline](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/model/ProviderPipeline.java)
