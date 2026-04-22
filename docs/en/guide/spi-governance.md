# SPI Governance

This document defines the governance model for Kairo's Service Provider Interfaces (SPIs). It covers the SPI registry, compatibility levels, deprecation policy, and migration guide template.

## SPI Registry

All public SPIs are listed below with their location and stability level.

| SPI Interface | Module | Package | Stability |
|---|---|---|---|
| `ModelProvider` | kairo-api | `io.kairo.api.model` | Stable |
| `ToolHandler` | kairo-api | `io.kairo.api.tool` | Stable |
| `ToolExecutor` | kairo-api | `io.kairo.api.tool` | Stable |
| `McpPlugin` | kairo-api | `io.kairo.api.mcp` | Stable |
| `MemoryStore` | kairo-api | `io.kairo.api.memory` | Stable |
| `EmbeddingProvider` | kairo-api | `io.kairo.api.memory` | Experimental |
| `UserApprovalHandler` | kairo-api | `io.kairo.api.tool` | Stable |
| `ElicitationHandler` | kairo-mcp | `io.kairo.mcp` | Experimental |

## Compatibility Levels

### Stable

Backward-compatible across minor versions. Breaking changes only in major versions with a **2-minor-version deprecation notice**.

- Method signatures will not change within the same major version.
- New methods added to the interface will include `default` implementations so existing implementations continue to compile.
- Behavioral semantics documented in Javadoc are part of the contract.

### Experimental

May change in minor versions. Marked with `@apiNote Experimental`.

- The API shape may evolve based on user feedback and real-world usage.
- Consumers should expect possible breaking changes between minor releases.
- Experimental SPIs are candidates for promotion to Stable once the design stabilizes.

### Internal

Not part of the public API. May change without notice.

- Classes in `*.internal` packages or annotated with `@ApiStatus.Internal`.
- No backward-compatibility guarantees whatsoever.
- Should never be referenced by external code.

## Deprecation Policy

1. **Minimum 2 minor version notice** before removal of any Stable SPI.
2. Use `@Deprecated(since = "x.y", forRemoval = true)` annotation on the deprecated type or method.
3. Provide a **migration guide** in the CHANGELOG for every deprecated SPI.
4. The replacement SPI must be available in the same release that introduces the deprecation.
5. Experimental SPIs may be removed with 1 minor version notice.

### Timeline Example

| Version | Action |
|---|---|
| v0.7 | `OldSpi` deprecated; `NewSpi` introduced |
| v0.8 | `OldSpi` still present, migration docs available |
| v0.9 | `OldSpi` removed |

## Migration Guide Template

When an SPI is deprecated and replaced, a migration guide following this template must be added to the CHANGELOG:

```
## Migration: [Old SPI] → [New SPI]

**Version**: v0.X → v0.Y

**Reason**: [Why the change was needed]

**Steps**:
1. [Step 1 — e.g., replace import from old package to new package]
2. [Step 2 — e.g., rename method calls]
3. [Step 3 — e.g., update configuration]

**Compatibility**: [Old SPI] is deprecated in v0.X and will be removed in v0.Z
```

## SPI Implementation Guidelines

1. **Thread safety**: All SPI implementations must be safe for concurrent use unless explicitly documented otherwise.
2. **Reactive types**: SPIs returning `Mono` or `Flux` must never block the calling thread. Use `subscribeOn(Schedulers.boundedElastic())` for blocking I/O.
3. **Error handling**: Implementations should throw domain-specific exceptions (e.g., `ToolException`, `MemoryStoreException`) rather than generic exceptions.
4. **Cooperative cancellation**: Long-running implementations should observe `CancellationSignal` from Reactor Context and terminate promptly when cancelled.

## Versioning Reference

| SPI Interface | Introduced |
|---|---|
| `ModelProvider` | v0.1.0 |
| `ToolHandler` | v0.1.0 |
| `ToolExecutor` | v0.1.0 |
| `MemoryStore` | v0.1.0 |
| `McpPlugin` | v0.4.0 |
| `UserApprovalHandler` | v0.4.0 |
| `EmbeddingProvider` | v0.5.0 |
| `ElicitationHandler` | v0.5.0 |
