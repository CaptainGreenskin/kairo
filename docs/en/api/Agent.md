# Agent — API Reference

**Package:** `io.kairo.api.agent`
**Stability:** `@Stable(since = "1.0.0")` — "Core ReAct contract; shipped since v0.1 and unchanged"
**Canonical source:** [`Agent.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/agent/Agent.java)

The runnable unit of work in Kairo. An agent accepts a `Msg`, runs its internal ReAct
loop (Thought → Action → Observation) through the configured `ModelProvider` and tools,
and returns a final `Msg` on the reactive boundary.

## Surface

```java
public interface Agent {
    Mono<Msg> call(Msg input);
    String id();
    String name();
    AgentState state();
}
```

## Stability Guarantees

- Binary compatibility held across v1.x per ADR-023.
- New `default` methods may be added to this interface.
- Removing `call(Msg)`, `id()`, `name()`, or `state()` requires a major bump.

## Default Implementations

| Impl | Module | Notes |
|------|--------|-------|
| `DefaultReActAgent` | `kairo-core` | Production-grade ReAct loop with tool dispatch, context compaction, hooks. |
| Custom | User | Implement `Agent` to bypass the default loop — e.g., single-turn LLM call. |

## Usage Example

```java
Agent agent = DefaultReActAgent.builder()
    .modelProvider(new AnthropicProvider(apiKey))
    .registerTool(new SearchTool())
    .build();

Msg reply = agent.call(Msg.user("Summarize today's PRs")).block();
```

Runnable: [`kairo-examples/.../quickstart/AgentExample.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-examples/src/main/java/io/kairo/examples/quickstart/AgentExample.java).

## Lifecycle

1. Instantiated once per conversation / session (typically via builder).
2. `call(Msg)` is invoked per user turn; each call runs the full ReAct loop.
3. Thread-safe when the default implementation is used; custom implementations must document their own contract.

## Migration Policy

`@Stable` — breaking changes go through ADR + japicmp
(`docs/governance/japicmp-policy.md`), deprecation first, removal in the next major.

## Related

- ADR: [ADR-001 — ReAct loop decomposition](../../adr/ADR-001-react-loop-decomposition.md)
- Census entry: [`spi-census-v1.0.md` → agent](../../governance/spi-census-v1.0.md)
