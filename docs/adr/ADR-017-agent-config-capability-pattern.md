# ADR-017 — AgentConfig capability pattern (v0.10)

## Status

Accepted — implemented in `v0.10.0` (scaffolding + derived accessors; full field removal is follow-up).

## Context

`AgentConfig` accumulated many orthogonal concerns (MCP, loop detection, durable execution, evolution, prompt contributors). This increases cognitive load for starters and makes “optional capabilities” harder to express without bloating the core record.

## Decision

Introduce **capability-shaped configuration records** in `kairo-api`:

- `McpCapabilityConfig`
- `LoopDetectionConfig`
- `DurableCapabilityConfig`

`AgentConfig` exposes **derived accessors**:

- `mcpCapability()`
- `loopDetection()`

Builders gain explicit capability entry points (`mcpCapability(...)`, `loopDetectionConfig(...)`, `durableCapability(...)` on `AgentBuilder`).

As an intermediate step toward full decoupling, legacy per-field MCP builder methods are marked `@Deprecated(forRemoval=true)` with guidance to migrate to `mcpCapability(...)`.

## Consequences

- **Pros**: clearer mental model; starters can migrate incrementally; reduces future Expert Team wiring complexity.
- **Cons**: temporary duplication (capability view + legacy fields) until MCP migrates fully to starter-only wiring.

## Follow-ups

- Remove MCP fields from the `AgentConfig` record entirely once `kairo-spring-boot-starter-mcp` wires MCP exclusively via `AgentBuilderCustomizer`.
