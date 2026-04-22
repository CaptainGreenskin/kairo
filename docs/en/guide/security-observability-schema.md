# Security Observability Schema

This document defines the field naming conventions and cardinality constraints for Kairo's security event schema, introduced in v0.7.

## Field Naming Rules

All security event fields use **dot notation** for compatibility with structured logging and observability backends (e.g., ELK, Datadog, Grafana Loki).

- Prefix: `security.`
- Segments are lowercase, separated by dots.
- No array indices or dynamic keys in field names.

## Low-Cardinality Constraints

Security event attributes **must** contain only low-cardinality values:

- **No** request IDs, trace IDs, or UUIDs.
- **No** raw input content (prompts, tool arguments, model responses).
- **No** unbounded user-supplied strings.
- Attribute values must come from a **bounded, enumerable set** wherever possible.

## Field Reference

| Field | Type | Description | Example |
|---|---|---|---|
| `security.event.type` | `SecurityEventType` enum | The classification of the event | `GUARDRAIL_DENY` |
| `security.policy.name` | String (bounded set) | The policy that produced the decision | `content-filter` |
| `security.decision.action` | Enum: ALLOW / DENY / MODIFY / WARN | The action taken by the policy | `DENY` |
| `security.decision.reason` | String (max 256 chars) | Human-readable reason for the decision | `PII detected in output` |
| `security.target.name` | String (bounded set) | The tool or model name being guarded | `echo`, `claude-3` |
| `security.target.type` | Enum: MODEL / TOOL / MCP_TOOL | The type of target being guarded | `TOOL` |
| `security.agent.name` | String (bounded set) | The agent that owns the pipeline | `assistant` |
| `security.event.timestamp` | ISO-8601 Instant | When the event was created | `2026-04-22T10:15:30Z` |
| `security.guardrail.phase` | `GuardrailPhase` enum | The pipeline boundary point | `PRE_TOOL` |

## SecurityEventType Values

| Value | Description |
|---|---|
| `GUARDRAIL_ALLOW` | Policy allowed the request/response to proceed |
| `GUARDRAIL_DENY` | Policy denied and halted the pipeline |
| `GUARDRAIL_MODIFY` | Policy modified the payload before continuing |
| `GUARDRAIL_WARN` | Policy logged a warning but allowed continuation |
| `PERMISSION_DENY` | Permission guard denied tool execution |
| `MCP_BLOCK` | MCP static guardrail policy blocked the request |

## OTel Integration

No OpenTelemetry exporter is included in v0.7. The default `LoggingSecurityEventSink` writes structured log entries via SLF4J. An OTel-based `SecurityEventSink` implementation is planned for v0.8.
