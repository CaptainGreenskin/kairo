# ADR-009: MCP Security Default Policy

## Status
Accepted (v0.7)

## Context

MCP (Model Context Protocol) servers expose tools from external and potentially untrusted
sources. The current `McpServerConfig` has no security configuration — all tools are
implicitly allowed, which is an unsafe default for production deployments.

Kairo needs a "deny-safe" default that blocks unconfigured tools while allowing explicitly
approved ones. This policy must integrate with the Guardrail SPI (ADR-007) without creating
duplicate enforcement paths or split audit trails.

## Decision

### Default Security Policy

Default security policy is `DENY_SAFE` — unconfigured MCP tools are blocked by default.

`McpSecurityPolicy` enum with 3 values:
- `ALLOW_ALL` — all tools permitted (opt-in for trusted servers)
- `DENY_SAFE` — only explicitly allowed tools permitted (default)
- `DENY_ALL` — all tools blocked (kill-switch for incident response)

### McpServerConfig Extension

`McpServerConfig` extended via Builder pattern (record-style, immutable) with 5 new fields:

| Field | Type | Default |
|-------|------|---------|
| `securityPolicy` | `McpSecurityPolicy` | `DENY_SAFE` |
| `allowedTools` | `Set<String>` | `null` (use policy default) |
| `deniedTools` | `Set<String>` | empty set |
| `maxConcurrentCalls` | `int` | `10` |
| `schemaValidation` | `boolean` | `true` |

### Authority Chain — Single Implementation Point

MCP static policy is implemented as `McpStaticGuardrailPolicy` (order = `Integer.MIN_VALUE`)
within `DefaultGuardrailChain` — NOT as a separate gate in `McpToolExecutor`.

This unifies all PRE_TOOL enforcement into one chain with one audit trail.

**Evaluation order within chain:**
1. `McpStaticGuardrailPolicy` (order = `MIN_VALUE`) — config-driven, fast-path DENY
   short-circuits the chain.
2. User-registered `GuardrailPolicy` instances (order = 0+) — context-aware dynamic decisions.

**Benefits:**
- Single audit trail for all tool-level security decisions.
- Consistent reason format across MCP and non-MCP denials.
- No duplicate rejection paths — one interception point.

### MCP Origin Metadata

`McpToolExecutor` sets `metadata.put("mcp.server", serverName)` in `GuardrailContext`.
`McpStaticGuardrailPolicy` checks this key to identify MCP-sourced tools and look up the
correct `McpServerConfig` for policy evaluation.

### Schema Validation

When `schemaValidation = true`, tool input is validated against the tool's JSON Schema
before execution. Validation failures produce `DENY` with a descriptive reason including
the specific validation error.

## Consequences

- **Positive**: Existing MCP users get `DENY_SAFE` by default — secure out of the box.
- **Positive**: `McpServerConfig` record extended with 5 new fields via Builder (backward compatible).
- **Positive**: Single `McpStaticGuardrailPolicy` replaces what could have been a separate
  enforcement path — unified audit.
- **Positive**: All MCP security decisions flow through `GuardrailChain` → `SecurityEventSink`
  for unified observability.
- **Negative**: `DENY_SAFE` default is a behavior change — requires migration documentation
  for users upgrading from v0.6 who relied on implicit ALLOW_ALL.
- **Negative**: Schema validation adds latency per tool call when enabled (default: on).

## References

- ADR-007 (Guardrail SPI Design)
- `McpServerConfig.java`
- MCP Specification (modelcontextprotocol.io)

## Spring Boot Configuration

Example `application.yml` configuring MCP server security:

```yaml
kairo:
  mcp:
    servers:
      my-server:
        transport-type: STREAMABLE_HTTP
        url: https://mcp.example.com
        security-policy: DENY_SAFE
        allowed-tools:
          - search
          - read-file
        denied-tools:
          - execute-command
        max-concurrent-calls: 5
        schema-validation: true
      trusted-internal:
        transport-type: STDIO
        command: ["npx", "-y", "@internal/mcp-server"]
        security-policy: ALLOW_ALL
```
