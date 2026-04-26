# ADR-019 — Hook API consolidation (`HookPhase` + `@HookHandler`) (v0.10)

## Status

Accepted — implemented in `v0.10.0` as an **additive coexistence** migration (legacy annotations remain).

## Context

Hook dispatch historically used many annotations (`@PreReasoning`, `@OnSessionEnd`, ...). This increases discovery complexity and makes static analysis/tooling harder.

## Decision

Introduce:

- `HookPhase` enum — canonical lifecycle phases.
- `@HookHandler(HookPhase)` — single annotation for hook methods.
- `HookEvent` marker interface implemented by existing hook event types.

Update `DefaultHookChain` discovery so each fire path matches **either**:

- legacy annotation (existing behavior), or
- `@HookHandler` with the mapped `HookPhase`.

## Consequences

- **Pros**: new code can standardize on one annotation; reduces long-term maintenance burden.
- **Cons**: temporary dual-discovery until legacy annotations are deleted in a later release.

## Follow-ups

- Delete legacy hook annotations once all internal + documented examples migrate.
- Consider a compile-time module test limiting `kairo-api/hook` file count after deletion wave.
