# ADR-022 — KairoEventBus → OpenTelemetry Exporter (v0.9)

## Status

Accepted — implemented in `v0.9.0`. Exporter lives in `kairo-observability` (`io.kairo.observability.event.KairoEventOTelExporter`); auto-configuration ships in `kairo-spring-boot-starter-observability`. Supersedes the stub note in the v0.7 security-observability schema ("An OTel-based `SecurityEventSink` implementation is planned for v0.8") — v0.9 turns that plan into a concrete exporter that covers every `KairoEventBus` domain, not just security.

## Context

ADR-018 introduced `KairoEventBus` as the single publication surface for execution, evolution, security, and team-orchestration envelopes. With the bus in place, shipping observability is no longer each emitter's concern — but we still needed a concrete bridge to OpenTelemetry for operators who run Grafana / Datadog / Splunk on the other end.

The v0.9 remaining-P0 scoping doc (`docs/roadmap/v0.9-remaining-p0-scoping.md`, item P0#3 / D3) locked the shape:

- Bridge every `KairoEvent` envelope to an OTel `LogRecord` (not span, not metric) — logs match the "point-in-time structured event" model every existing Kairo sink already uses.
- Security envelopes are **always-on**; execution/team/evolution honour a configurable `samplingRatio` so sustained throughput does not drown the backend.
- Attribute namespace is `kairo.<domain>.*` — every bus attribute gets flattened under this prefix so dashboards can pivot across domains without colliding with unrelated OTel resource attributes.
- The exporter must never throw across the bus boundary: a downstream OTel failure must not break Kairo's publish path.

## Decision

### Component surface

- `KairoEventOTelExporter` (final class, `@Experimental` through v0.9):
  - Constructor: `(KairoEventBus, LoggerProvider, Set<String> includeDomains, double samplingRatio, @Nullable Pattern redactKeys)`.
  - Lifecycle: `start()` subscribes to `bus.subscribe()`; `stop()` disposes the subscription. Both idempotent.
  - Counters: `exportedCount()`, `droppedByDomainCount()`, `droppedBySamplingCount()` — exposed for tests and lightweight dashboards. Production deployments should prefer their own OTel SDK metrics.
  - Errors inside `export()` are caught, logged at WARN, and swallowed — the bus never sees them.

### Mapping contract

- Envelope-level attributes on every record: `kairo.event.id`, `kairo.domain`, `kairo.event.type`.
- Per-domain attributes: for each entry in `event.attributes()`, the flat key is `kairo.<domain>.<originalKey>` and the value is `String.valueOf(v)`. Nested payload objects are **not** flattened — publishers are responsible for producing bus-safe attributes.
- `LogRecord.body` = `event.eventType()`; `LogRecord.severityText` = domain name; `LogRecord.observedTimestamp` = `event.timestamp()`.
- Severity = `WARN` for `security`, `INFO` for every other domain.

### Filtering + sampling

- `includeDomains` drops any envelope whose `domain()` is not in the set. Counted in `droppedByDomainCount`.
- Non-security envelopes are additionally gated by `ThreadLocalRandom.nextDouble() < samplingRatio`. Ratio is validated on construction; only `[0.0, 1.0]` is accepted. Security envelopes skip this check entirely.

### Redaction

- Optional `Pattern` matched against the *flat* key (e.g. `kairo.execution.prompt`). On match, the value is replaced with the literal `<redacted>` before being attached to the record. Redaction is key-driven because attribute values are already opaque to the exporter; operators compose the pattern from the starter's `redactAttributePatterns` list.

### Spring Boot starter

`kairo-spring-boot-starter-observability`:

- Opt-in via `kairo.observability.event-otel.enabled=true` (default `false`, consistent with every other v0.9 starter).
- Wires a single `KairoEventOTelExporter` bean when a `LoggerProvider` bean is available. Without `LoggerProvider` the starter deliberately stays inert — applications bring their own OTel SDK (typically the OTel Spring Boot starter), matching the deny-safe posture in ADR-021.
- `auto-start=true` (default) drives `exporter.start()` in the bean factory; operators who want manual lifecycle flip it off.
- Default `include-domains=[security]`, `sampling-ratio=1.0`, no redaction. Execution/team/evolution require explicit opt-in.

## Consequences

- **Pros**
  - One contract covers every bus domain — operators dashboard-once, not per-subsystem.
  - Sampling keeps Kairo honest under load: a chatty execution log does not DoS the OTel backend, but security decisions always make it out.
  - Redaction is enforced in the exporter, not the publisher, so the bus keeps full fidelity for in-process subscribers while external observability is sanitised.
  - Deny-safe starter posture — no auto-configured transport unless the application brings a `LoggerProvider`.
- **Cons**
  - Flattening-only: nested structures in `event.attributes()` are stringified, which loses structure. Publishers that care must pre-flatten (documented in the security-observability-schema guide).
  - Uniform sampler: one ratio across execution/team/evolution. Per-domain sampling is deferred — adapters that need it can wrap the bus with a domain-specific filter before publishing.
  - Sampling uses `ThreadLocalRandom`, not a deterministic hash over trace id. That is fine for log-record sampling but means two different Kairo processes won't agree on which envelope to drop. Future work: parent-based sampling once Kairo emits trace context.
- **Deferred to post-v0.9**
  - Span emission for execution envelopes (OTel `Tracer` integration). Logs were deliberately chosen first because every existing Kairo sink expects a point-in-time record, not a span.
  - Parent-based / trace-id sampling (needs Kairo to emit trace context on the bus first).
  - Metric aggregates (e.g. `kairo.events.dropped.total` as an OTel counter). Today we expose `droppedBySamplingCount()` / `droppedByDomainCount()` as plain Java getters; migrating them to `LongCounter` is a drop-in change.

## Non-goals (v0.9)

- Replacing `LoggingSecurityEventSink`. The SLF4J sink keeps running in parallel for legacy dashboards; the OTel path is strictly additive.
- Shipping an OTLP exporter inside this module. Applications wire the concrete OTel exporter (OTLP / Jaeger / Zipkin / File) themselves via their `LoggerProvider`.
- A metrics pipeline. Logs only in v0.9; metrics and spans come with ADR-023/024 when we have real telemetry from v0.9 adopters to justify their shape.
