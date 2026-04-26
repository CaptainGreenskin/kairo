# Security & Event Observability Schema

This document defines the field naming conventions and cardinality constraints for Kairo's event-bus → OpenTelemetry integration.

**Status:** v0.9 updates the schema to cover every `KairoEventBus` domain, not just security. The original `security.*` keys predate the bus (see ADR-018) and are **preserved verbatim inside the envelope body** for backwards compatibility with existing `LoggingSecurityEventSink` consumers. When the envelope is bridged to OpenTelemetry by `KairoEventOTelExporter`, a new namespace prefix `kairo.<domain>.*` is applied on top.

## Attribute Namespace

Every log record emitted by `KairoEventOTelExporter` carries three envelope-level keys:

| Key | Type | Example |
|---|---|---|
| `kairo.event.id` | String (UUID) | `9c1b…-…-…` |
| `kairo.domain` | String (enum) | `security`, `execution`, `evolution`, `team` |
| `kairo.event.type` | String | `GUARDRAIL_DENY`, `MODEL_TURN`, `EXPERT_TEAM_ROUND` |

Additional per-domain attributes are flattened under `kairo.<domain>.<key>` — the exporter copies each entry of `KairoEvent.attributes()` into this namespace. Values are converted to strings; structured payloads MUST be flattened by the publisher before entering the bus.

Severity mapping:

- `security` → `WARN`
- `execution` / `evolution` / `team` → `INFO`

`LogRecord.severityText` is set to the domain name; `LogRecord.body` is set to the event type; `LogRecord.observedTimestamp` uses `KairoEvent.timestamp()`.

## Domain Schemas

### `kairo.security.*`

Used for guardrail lifecycle events. Always-on in the OTel exporter (never sampled out).

| Field | Type | Description | Example |
|---|---|---|---|
| `kairo.security.event.type` | `SecurityEventType` enum | Classification of the security decision | `GUARDRAIL_DENY` |
| `kairo.security.policy.name` | String (bounded set) | Policy that produced the decision | `content-filter` |
| `kairo.security.decision.action` | `ALLOW` / `DENY` / `MODIFY` / `WARN` | Outcome of the policy | `DENY` |
| `kairo.security.decision.reason` | String (max 256 chars) | Human-readable reason | `PII detected in output` |
| `kairo.security.target.name` | String (bounded set) | Tool or model being guarded | `echo`, `claude-3` |
| `kairo.security.target.type` | `MODEL` / `TOOL` / `MCP_TOOL` | Type of target | `TOOL` |
| `kairo.security.agent.name` | String (bounded set) | Agent owning the pipeline | `assistant` |
| `kairo.security.guardrail.phase` | `GuardrailPhase` enum | Pipeline boundary point | `PRE_TOOL` |

### `kairo.execution.*`

Used for execution-log lifecycle (model turns, tool calls, compaction, iterations). Sampled via `kairo.observability.event-otel.sampling-ratio`.

| Field | Type | Description | Example |
|---|---|---|---|
| `kairo.execution.model` | String (bounded set) | Model identifier | `claude-opus-4-7` |
| `kairo.execution.tool` | String (bounded set) | Tool name when applicable | `bash`, `read` |
| `kairo.execution.phase` | Enum | Lifecycle phase | `MODEL_TURN`, `TOOL_CALL`, `COMPACT` |
| `kairo.execution.iteration` | Integer | Iteration ordinal within a run | `3` |
| `kairo.execution.tokens.input` | Integer | Input tokens | `12540` |
| `kairo.execution.tokens.output` | Integer | Output tokens | `812` |
| `kairo.execution.duration_ms` | Integer | Elapsed ms for the span | `1824` |

### `kairo.evolution.*`

Used for self-evolution skill governance lifecycle (proposal → review → apply).

| Field | Type | Description | Example |
|---|---|---|---|
| `kairo.evolution.skill.id` | String (bounded set) | Skill identifier | `summarize-diff` |
| `kairo.evolution.phase` | Enum | Governance phase | `PROPOSED`, `REVIEWED`, `APPLIED`, `ROLLED_BACK` |
| `kairo.evolution.reviewer` | String (bounded set) | Reviewer principal | `sre-ops` |
| `kairo.evolution.outcome` | Enum | Review outcome | `ACCEPTED`, `REJECTED` |

### `kairo.team.*`

Used for multi-agent orchestration lifecycle (Expert Team, v0.10+ surfaces populate it).

| Field | Type | Description | Example |
|---|---|---|---|
| `kairo.team.id` | String (bounded set) | Team identifier | `triage-team` |
| `kairo.team.round` | Integer | Round ordinal within a conversation | `2` |
| `kairo.team.role` | String (bounded set) | Contributing role | `triage`, `analyst`, `reporter` |
| `kairo.team.expert.id` | String (bounded set) | Expert agent id | `log-reader` |
| `kairo.team.transition` | Enum | State transition | `ROUND_START`, `ROUND_END`, `HANDOFF`, `CONSENSUS` |

## Low-Cardinality Constraints

Every domain above keeps attribute values inside a bounded, enumerable set:

- **No** request IDs, trace IDs, or UUIDs as *attribute values* (except `kairo.event.id` which is intentional and low-volume per-event).
- **No** raw input content (prompts, tool arguments, model responses) — these MUST stay in the original bus `payload`, which is not exported.
- **No** unbounded user-supplied strings.

Attributes that would otherwise be high-cardinality MUST be bucketed (e.g. duration → histogram on the OTel SDK side; user id → role).

## Attribute Redaction

`kairo.observability.event-otel.redact-attribute-patterns` is a list of regexes matched against the *flat* key (after namespacing). When a pattern matches, the value is replaced by the literal string `<redacted>` before being attached to the log record. Typical starter configuration:

```yaml
kairo:
  observability:
    event-otel:
      redact-attribute-patterns:
        - ".*password.*"
        - ".*token.*"
        - ".*secret.*"
```

Redaction happens in the exporter, not the publisher — the bus still sees raw attribute values, but external observability backends never do.

## OTel Integration (v0.9)

The `kairo-spring-boot-starter-observability` module wires `KairoEventOTelExporter` when:

1. `kairo.observability.event-otel.enabled=true` (default `false`, matching every other v0.9 starter).
2. A `KairoEventBus` bean is present (always true when `kairo-spring-boot-starter-core` is on the classpath).
3. A `LoggerProvider` bean is present (applications bring their own OTel SDK wiring — either via `opentelemetry-spring-boot-starter` or manual configuration).

Default `include-domains` is `[security]`. Execution/team/evolution domains require explicit opt-in:

```yaml
kairo:
  observability:
    event-otel:
      enabled: true
      include-domains: [security, execution, evolution]
      sampling-ratio: 0.2     # sampled for non-security; security is always-on
```

The previous `LoggingSecurityEventSink` still works in parallel — it writes structured SLF4J entries with the legacy `security.*` keys (no `kairo.` prefix), so existing dashboards keep functioning while teams migrate to the OTel path. See ADR-018 for the domain/envelope contract and ADR-022 for the exporter's behavioural guarantees.
