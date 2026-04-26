# Msg — API Reference

**Package:** `io.kairo.api.message`
**Stability:** `@Stable(since = "1.0.0")` — "Core message type; shape frozen since v0.1"
**Canonical source:** [`Msg.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/message/Msg.java)

The neutral envelope every component speaks: agent input, model output, tool calls, tool
observations. A role (`user` / `assistant` / `system` / `tool`), textual content, optional
tool calls, and optional metadata — no provider-specific shape leaks across the boundary.

## Surface (abridged)

```java
public class Msg {
    public static Msg user(String content);
    public static Msg assistant(String content);
    public static Msg system(String content);
    public static Msg tool(String toolName, String result);

    public String role();
    public String content();
    public List<ToolCall> toolCalls();
    public Map<String, Object> metadata();
}
```

## Stability Guarantees

- Factory method signatures and getter names are frozen across v1.x.
- New factories / default accessors may be added.
- Field order within serialized envelopes is not part of the contract.

## Default Implementations

`Msg` itself is a concrete, immutable-by-convention type — no polymorphism intended.
Providers convert `Msg` to their wire format and back via the `ProviderPipeline`
request-builder / response-parser stages.

## Usage Example

```java
Msg reply = agent.call(Msg.user("Draft a changelog entry")).block();
System.out.println(reply.content());
```

## Configuration

Conversation-level metadata (session id, trace id, user id) lives on `Msg.metadata()`.
Providers are encouraged to copy that metadata into their request envelopes for observability
correlation but MUST NOT depend on any specific key.

## Lifecycle

1. Produced by a caller or by an agent step (`AgentState` retention is managed by `ContextManager`).
2. Flows through the `ReActLoop` — model call → tool dispatch → tool observation → next model call.
3. Finalized when the loop terminates; retained in `AgentState` for follow-up turns.

## Migration Policy

`@Stable` — new fields additive, never destructive. Any change goes through ADR + japicmp.

## Related

- SPI: [`ToolCall`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/message/ToolCall.java)
- Context: [`ContextManager` / Compaction pipeline](../../adr/ADR-006-compaction-pipeline-architecture.md)
