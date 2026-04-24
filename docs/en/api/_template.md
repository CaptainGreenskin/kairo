# {{TypeName}} — API Reference

**Package:** `io.kairo.api.{{package}}`
**Stability:** `@Stable` (since v1.0.0)
**Since:** v{{firstShippedVersion}}
**Canonical source:** [`{{path}}`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/{{package}}/{{TypeName}}.java)

> One-line tagline for the type — what it models, who consumes it.

## Surface

```java
// Paste the actual public signature here — kept minimal, no prose.
```

## Stability Guarantees

- Binary compatibility held across v1.x per ADR-023.
- New default methods may be added to interfaces.
- New values may be added to enums (consumers should use `default` branches).
- Removals / signature changes require a major-version bump.

## Default Implementations

| Impl | Module | Notes |
|------|--------|-------|
| ... | ... | ... |

## Usage Example

```java
// Minimal runnable snippet — prefer linking an executable in kairo-examples over inline prose.
```

## Configuration

| Property / Builder | Default | Purpose |
|--------------------|---------|---------|
| ... | ... | ... |

## Lifecycle

1. When is the SPI instantiated?
2. When is it invoked (per-request, per-session, per-agent)?
3. Thread-safety expectations.

## Migration Policy

This type is `@Stable`. Breaking changes are gated through ADR + japicmp
(`docs/governance/japicmp-policy.md`). Deprecations must ship at least one minor
release before removal and be called out in `CHANGELOG.md`.

## Related

- ADR: `docs/adr/ADR-xxx.md`
- Census entry: `docs/governance/spi-census-v1.0.md`
- Tests: `kairo-api/src/test/java/io/kairo/api/{{package}}/{{TypeName}}Test.java`
