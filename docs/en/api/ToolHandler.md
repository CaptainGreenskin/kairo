# ToolHandler — API Reference

**Package:** `io.kairo.api.tool`
**Stability:** `@Stable(since = "1.0.0")` — "Tool execution contract; unchanged since v0.1"
**Canonical source:** [`ToolHandler.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/tool/ToolHandler.java)

Executes a single named tool invocation. The ReAct loop picks a tool by name and a
JSON-schema-validated input map, then dispatches here. Returning a `ToolResult` feeds the
observation back into the model's context.

## Surface

```java
public interface ToolHandler {
    ToolResult execute(Map<String, Object> input) throws Exception;
}
```

## Stability Guarantees

- `execute` signature frozen across v1.x.
- Default methods may be added, never removed.

## Default Implementations

| Impl | Module | Notes |
|------|--------|-------|
| `SearchTool`, `ReadFileTool`, `WriteFileTool`, ... | `kairo-tools` | Common reference tools with schemas and defaults. |
| Custom | User | Implement this SPI to expose any side-effecting or read-only capability. |

## Usage Example

```java
public final class SearchTool implements ToolHandler {
    @Override
    public ToolResult execute(Map<String, Object> input) {
        String q = (String) input.get("query");
        return ToolResult.ok(search(q));
    }
}
```

Runnable: [`kairo-examples/.../quickstart/FullToolsetExample.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-examples/src/main/java/io/kairo/examples/quickstart/FullToolsetExample.java).

## Configuration

Tools are registered against an `Agent` (or its builder) with a name + JSON schema describing
inputs. The schema drives model-side tool selection; the handler validates on the runtime side.

## Lifecycle

1. Instantiated once and registered with an agent.
2. `execute` may be called repeatedly, concurrently. Implementations must be thread-safe.
3. Errors should either return `ToolResult.failure(...)` (recoverable) or throw (fatal, surfaces as `ToolExecutionException`).

## Migration Policy

`@Stable` — breaking changes require ADR + major bump.

## Related

- Guide: [Exception mapping](../guide/exception-mapping.md)
- SPI: [`ToolResult`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/tool/ToolResult.java)
- Guardrail layer: [ADR-007](../../adr/ADR-007-guardrail-spi-design.md)
