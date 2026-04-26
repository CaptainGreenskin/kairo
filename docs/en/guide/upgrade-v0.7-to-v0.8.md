# Upgrade Guide: v0.7.x → v0.8.0

This guide covers the changes introduced in Kairo v0.8.0 and what you need to know when upgrading from v0.7.x.

> **TL;DR — No action required for existing users.** All new features are opt-in. Existing agent configurations work unchanged.

---

## New SPIs (Experimental)

### DurableExecutionStore

Provides checkpoint-based crash recovery with at-least-once semantics. Two built-in implementations are available:

- `InMemoryDurableExecutionStore` — for development and testing
- `JdbcDurableExecutionStore` — for production persistence

**Status**: `@Experimental` — the API may change in minor versions.

### ResourceConstraint

Unified iteration/token/timeout enforcement replacing ad-hoc checks. Provides a `validate()` + `onViolation()` contract with composable constraint actions (`ALLOW`, `WARN_CONTINUE`, `GRACEFUL_EXIT`, `EMERGENCY_STOP`).

**Status**: `@Experimental` — the API may change in minor versions.

---

## ToolContext Breaking Change

A new optional `idempotencyKey` field has been added to `ToolContext`.

- **Existing code using the 3-arg constructor** is backward-compatible — the key defaults to `null`.
- If you construct `ToolContext` manually, no changes are needed unless you want to opt into idempotency support.

---

## IterationGuards Change

`IterationGuards` now delegates to the `ResourceConstraint` chain when one is provided.

- **Existing behavior is unchanged** when no `ResourceConstraint` beans are injected.
- If you provide custom `ResourceConstraint` implementations, they will be composed with the existing iteration guard logic.

---

## ReActLoop Constructor

A new 6-arg overload has been added to `ReActLoop` that accepts an optional `ExecutionEventEmitter`.

- **The existing 5-arg constructor still works** and behaves identically to previous versions.
- The new overload enables event emission for durable execution tracking.

---

## New Annotations

Two new annotations are available in `io.kairo.api.tool` for tool replay safety:

| Annotation | Meaning |
|---|---|
| `@Idempotent` | Tool is safe to re-execute on replay (e.g., read-only queries) |
| `@NonIdempotent` | Tool must NOT be re-executed on replay; cached result is used instead |

**Default behavior**: Unannotated tools are treated as **non-idempotent** (safe default — cached result on replay).

---

## New Spring Boot Properties

All new properties are **opt-in and disabled by default**.

### Durable Execution

| Property | Default | Description |
|---|---|---|
| `kairo.execution.durable.enabled` | `false` | Enable DurableExecution |
| `kairo.execution.durable.store-type` | `memory` | `"memory"` or `"jdbc"` |
| `kairo.execution.durable.recovery-on-startup` | `true` | Auto-recover pending executions on startup |

### Cost-Aware Routing

| Property | Default | Description |
|---|---|---|
| `kairo.routing.model-tiers` | *(none)* | Model tier definitions with per-token pricing |
| `kairo.routing.fallback-chain` | *(none)* | Ordered list of fallback tier names |

---

## JDBC Migration

If you enable `kairo.execution.durable.store-type=jdbc`:

- Flyway migration `V1__create_execution_tables.sql` runs **automatically** on application startup.
- The migration creates the required `execution_records` and `execution_events` tables.
- Ensure your datasource is configured and Flyway is on the classpath (included transitively via `kairo-spring-boot-starter`).

---

## Summary

| Area | Impact | Action Required |
|---|---|---|
| DurableExecutionStore SPI | New (Experimental) | None — opt-in via properties |
| ResourceConstraint SPI | New (Experimental) | None — opt-in via bean injection |
| ToolContext field | Additive | None — defaults to null |
| IterationGuards | Behavioral (delegating) | None — unchanged without constraints |
| ReActLoop constructor | New overload | None — existing constructor works |
| @Idempotent / @NonIdempotent | New annotations | None — unannotated tools safe by default |
| Spring Boot properties | New (opt-in) | None — disabled by default |
| JDBC migration | Auto-applied if jdbc | Configure datasource if using jdbc store |
