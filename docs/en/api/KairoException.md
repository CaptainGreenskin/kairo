# KairoException — API Reference

**Package:** `io.kairo.api.exception`
**Stability:** `@Stable(since = "1.0.0")` — "Base exception with structured error fields"
**Canonical source:** [`KairoException.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/exception/KairoException.java)

Root of Kairo's exception hierarchy. Every recoverable or classifying failure in the
reactor extends this class, which carries structured error metadata (code, category,
retryability) so callers can branch without string-matching on messages.

## Surface (abridged)

```java
public class KairoException extends RuntimeException {
    public KairoException(String message);
    public KairoException(String message, Throwable cause);

    public String errorCode();
    public ErrorCategory errorCategory();
    public boolean retryable();
    public Map<String, Object> attributes();
}
```

## Stability Guarantees

- Class name, package, constructor signatures, and getter names are frozen for v1.x.
- New fields may be added as structured attributes; no field removals.
- Subtypes live in `io.kairo.api.exception.*` and carry their own stability annotations.

## Subtype map

| Subtype | Purpose |
|---------|---------|
| `ToolExecutionException` | Tool handler threw or produced a fatal result. |
| `ModelInvocationException` | Provider-side failure (HTTP / parse / rate-limit). |
| `ModelUnavailableException` | Provider temporarily unreachable — typically retryable. |
| `GuardrailException` | Guardrail denied the request / response. |
| `MiddlewareRejectException` | Middleware short-circuited the request. |
| `ContextOverflowException` | Context exceeds token budget after compaction. |

See `docs/en/guide/exception-mapping.md` for the full hierarchy and the reactive-boundary
mapping contract.

## Usage Example

```java
try {
    agent.call(Msg.user(input)).block();
} catch (KairoException ex) {
    if (ex.retryable()) {
        schedule(ex);
    } else {
        log.error("Fatal: code={}, category={}", ex.errorCode(), ex.errorCategory(), ex);
    }
}
```

## Migration Policy

`@Stable` — new subtypes are additive; retaining the root class is a v1.x hard guarantee.
Any change requires ADR + japicmp sign-off.

## Related

- ADR: [ADR-004 — Exception hierarchy design](../../adr/ADR-004-exception-hierarchy-design.md)
- ADR: [ADR-008 — Structured error fields](../../adr/ADR-008-exception-phase-b-structured-fields.md)
- Guide: [`exception-mapping.md`](../guide/exception-mapping.md)
