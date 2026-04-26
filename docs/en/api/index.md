# API Reference

Every type under `io.kairo.api.*` carries an explicit stability annotation:

| Annotation | Meaning | Break policy |
|------------|---------|--------------|
| `@Stable` | Public contract. Frozen for the v1.x window. | Requires ADR + major bump. |
| `@Experimental` | Opt-in. Shape may change. | Can change with a minor bump. |
| `@Internal` | Not a public API. | May change any time. |

Canonical policy: [ADR-023 — SPI stability](../../adr/ADR-023-spi-stability-policy.md).
Enforcement: [japicmp policy](../../governance/japicmp-policy.md).
Full triage: [SPI census v1.0](../../governance/spi-census-v1.0.md).

## Core `@Stable` SPIs

| Type | Package | Purpose |
|------|---------|---------|
| [Agent](./Agent.md) | `agent` | Core ReAct contract for a runnable agent. |
| [ModelProvider](./ModelProvider.md) | `model` | Invoke an LLM; returns a `ModelResponse`. |
| [ToolHandler](./ToolHandler.md) | `tool` | Execute a named tool invocation. |
| [Msg](./Msg.md) | `message` | Wire message between agent, provider, and tools. |
| [KairoException](./KairoException.md) | `exception` | Base exception with structured error fields. |

## Where to look for the rest

- **`@Stable` surface (119 types)** — enumerated in the census, grouped by package.
- **`@Experimental` surface (78 types)** — a2a, middleware, team, evolution, channel, guardrail.
  Use at your own risk; shapes will change before v1.1 stabilization.
- **Source of truth** — when this page and the source disagree, the source wins.
  [`kairo-api/src/main/java/io/kairo/api/`](https://github.com/CaptainGreenskin/kairo/tree/main/kairo-api/src/main/java/io/kairo/api).

## Using the reference

Each entry lists: surface, stability guarantees, default implementations, usage example,
configuration, lifecycle, migration policy, and related ADRs. Pages are hand-maintained and
favor pointer fidelity over prose — when in doubt, follow the link to the source file.
