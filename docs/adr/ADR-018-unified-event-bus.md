# ADR-018 — Unified in-process event bus (`KairoEventBus`) (v0.10)

## Status

Accepted — implemented in `v0.10.0` (API + default implementation + optional publisher bridging).

## Context

Kairo emits important lifecycle signals across multiple domains:

- durable execution events (`ExecutionEvent` / `ExecutionEventEmitter`)
- evolution governance lifecycle (`EvolutionEventType`)
- security observability (`SecurityEvent` / `SecurityEventSink`)

Downstream observability (OTel, metrics, audit sinks) needs a **single subscription surface** without forcing every emitter to learn transport details.

## Decision

Add `KairoEventBus` + `KairoEvent` envelope:

- `KairoEvent` is a **domain-tagged envelope** (`execution`, `evolution`, `security`, `team`) with optional strongly-typed `payload`.
- `DefaultKairoEventBus` uses a multicast Reactor sink for fan-out.

Bridge points (optional, caller-driven in v0.10):

- `ExecutionEventEmitter(store, executionId, bus?)` publishes after append preparation.
- `EvolutionPipelineOrchestrator(..., bus?)` publishes lifecycle milestones.
- `BusBridgingSecurityEventSink` composes an existing sink + bus publish.

## Consequences

- **Pros**: one subscription API for cross-domain observability; avoids overloading `ExecutionEventType` with unrelated domains.
- **Cons**: Spring auto-wiring is not yet mandatory; callers must opt-in until a follow-up auto-configuration lands.

## Non-goals (v0.10)

- Replacing durable execution persistence with the bus (the bus is not a store).
- Merging enums across domains (each domain keeps its own `*EventType`).
