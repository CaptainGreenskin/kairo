# ADR-027 — TenantContext (v1.1)

## Status

Accepted — implemented in `v1.1.0` (SPI in `kairo-api/.../tenant/`, `ThreadLocalTenantContextHolder` default in `kairo-core`, Reactor `Context` propagator, passive consumption wired into `KairoEventBus`, `KairoEventOTelExporter`, `SecurityEventSink`, `ToolContext`). Replaces the absence of any cross-cutting tenant identity through v1.0.

## Context

Through v1.0, every cross-cutting concern (audit, quota, log, metric, cost) had no canonical hook to aggregate by tenant. `kairo-security-pii`'s `JdbcAuditEventSink` (v1.0) writes per-record audit rows but cannot tag them with a tenant id; OTel spans cannot project a `kairo.tenant.id` attribute; `ComplianceReport` cannot break down counters per organization. The single-tenant default works fine — but the moment a deployment is multi-tenant, every observability surface is tenant-blind.

`.plans/V1.1-SPI-FOUNDATIONS.md` F3 scopes the v1.1 fix narrowly: **deliver context propagation only**. Quota enforcement, physical isolation, and cross-tenant routing all need a tenant identity to exist before they're meaningful — but they do **not** belong in v1.1. They are v1.2 (per `.plans/V1.2-DISTRIBUTED.md` D1).

## Decision

Introduce `io.kairo.api.tenant.*` as the stable contract for passive multi-tenant identity propagation:

| Type | Role |
|---|---|
| `TenantContext` (record) | `tenantId` / `principalId` / `attributes` — the value object |
| `TenantContext.SINGLE` | Sentinel `("default", "anonymous", {})` observed when nothing is bound |
| `TenantContext.ATTR_TENANT_ID` / `ATTR_PRINCIPAL_ID` | Canonical attribute keys for sink projection |
| `TenantContextHolder` | `current()` + `bind(ctx) → Scope` propagation seam |
| `TenantContextHolder.NOOP` | Sentinel holder that always returns `SINGLE`, drops `bind()` silently |

Default implementations (in `kairo-core`):

- `ThreadLocalTenantContextHolder` — thread-local stack, `bind()` returns an `AutoCloseable` `Scope` that pops on close. Nested binds restore the previous value on close (LIFO discipline).
- `ReactorTenantContextPropagator` — bridges the thread-local onto the Reactor `Context`, so `TenantContext.current()` survives `subscribeOn` / `publishOn` boundary hops in reactive pipelines. Wired via `Hooks.onEachOperator` at framework startup.

### Propagation rules

1. **Boundary code binds; downstream reads.** HTTP filters, message consumers, scheduled-job entry points are the *only* call sites that should call `holder.bind(ctx)`. Internal code reads via `holder.current()` and never re-binds.
2. **Try-with-resources is mandatory for `bind()`.** A leaked scope corrupts the thread-local stack for subsequent requests. The `Scope.close()` contract is "never throws checked exceptions"; implementations MUST ensure close is safe to call multiple times.
3. **Reactor pipelines inherit at subscribe time.** The propagator captures the binding when the pipeline is subscribed and re-binds it on each downstream signal. Upstream `Context.put(TENANT_KEY, ctx)` overrides the captured value.
4. **`SINGLE` is the implicit default.** Every read site that observes no binding sees `SINGLE`. Existing single-tenant deployments observe zero behaviour change.

### Passive consumption sites (v1.1)

Five sinks read `TenantContext.current()` and project it onto their existing surface — *no method signatures change*:

| Sink | Projection |
|---|---|
| `KairoEventBus.publish(...)` | Attaches `tenant.id` + `tenant.principal` to event attributes if not already present |
| `KairoEventOTelExporter` | Emits OTel attributes `kairo.tenant.id`, `kairo.tenant.principal` on every span / log record |
| `SecurityEventSink.record(...)` | Adds `tenant.id` to the audit event attribute map |
| `ToolContext.tenant()` | Accessor exposed for tools that genuinely need tenant identity (e.g. tenant-aware DB tools) |
| `BridgeMeta.tenant` (ADR-028) | Bridge frames inherit the propagated tenant on inbound dispatch |

These are **read-only consumers**. None of them changes its public method signatures; the tenant id rides on the existing attribute map of each surface.

## Consequences

- **Pros**
  - Audit / OTel / event bus all aggregate by tenant the moment a single boundary call binds a `TenantContext`. No tool code changes, no method signatures change.
  - `SINGLE` sentinel preserves backward compatibility for single-tenant deployments — they see no diff.
  - Reactor `Context` bridge means tenant identity survives async hops (the canonical Java multi-tenant landmine).
  - The `TenantContextHolder` SPI is intentionally minimal so future v1.2 extensions (e.g. tenant-aware `Resilience4j` rate limiters, tenant-scoped Kafka topic prefixes) can compose by wrapping the holder, not replacing it.
- **Cons**
  - Pure passive consumption means the runtime cannot *enforce* a tenant boundary in v1.1. A tool that ignores `tenant()` and writes globally is still possible. This is intentional — enforcement is v1.2.
  - Thread-local state is well-known to leak under exotic execution models (custom thread pools that don't propagate). The Reactor bridge handles the standard reactive case; the burden of correct propagation in custom thread pools remains on the application.
- **Deferred to post-v1.1**
  - **Quota enforcement** (per-tenant token / cost / request-rate limits) — v1.2 (`.plans/V1.2-DISTRIBUTED.md` D1).
  - **Physical isolation** (per-tenant DB schemas, per-tenant Kafka topic prefixes, per-tenant message bus partitioning) — v1.2.
  - **Cross-tenant routing** (`Channel` adapters that fan a single inbound message to multiple tenant-scoped agents) — v1.3.
  - **Tenant-aware `Resilience4j` integrations** — v1.2 alongside quota work.

## Non-goals (v1.1)

- **No method-signature changes.** Existing public APIs do not gain a `TenantContext` parameter. Consumption is via `TenantContextHolder.current()` only.
- **No quota enforcement, no rate limiting, no isolation.** v1.1 is the propagation seam, not the policy engine. Anything that sounds like enforcement is v1.2.
- **No persistence layer**. Tenant ids ride on existing attribute maps of existing events / spans / audit rows. We do not introduce a `tenants` table.
- **No authentication / authorization.** Spring Security upstream owns auth; v1.1 only consumes the result via `bind()` at boundary code.
- **No automatic discovery.** The framework does not introspect HTTP headers / JWTs to derive a tenant — application code does that and calls `bind()`.

## Propagation rule violations

- **Symptom**: `TenantContext.current()` returns `SINGLE` inside what should be a tenant-bound code path.
- **Likely causes**:
  1. Boundary code didn't call `bind()`.
  2. Custom thread pool used without propagation wrapping.
  3. `Scope.close()` was called too early (try-with-resources block ended before the async work completed).
- **Diagnostic**: `KairoEventBus` emits a `tenant.id=default` attribute on the event — search audit / OTel for unexpected `default` ids in production.

## Future-extension rules

When v1.2 quota enforcement lands:

1. The `TenantContext` record gains no new components — quota policy lives on a separate `TenantQuotaPolicy` SPI keyed by `tenantId`.
2. Quota enforcement composes by *reading* `TenantContextHolder.current()` and rejecting / throttling at the boundary surface (model provider, sandbox start, channel send) — *not* by mutating `TenantContext`.
3. New attribute keys SHOULD be added to `TenantContext.attributes` (free-form map) before earning a static `ATTR_*` constant. Promotion to a constant is a v1.x additive change.

The `TenantContextHolder` interface is intentionally **not** sealed — third parties can implement it to bridge alternative propagation layers (gRPC `Context`, Kafka `Headers`, Spring Cloud Sleuth) without waiting for the framework to ship them. The `NOOP` sentinel exists for deployments that opt out of tenant tracking.

## Related documents

- `.plans/V1.1-SPI-FOUNDATIONS.md` — F3 spec + anti-goals
- `.plans/V1.2-DISTRIBUTED.md` — D1 distributed tenant work this seam unlocks
- `kairo-api/src/main/java/io/kairo/api/tenant/package-info.java` — package-level Javadoc
- `docs/roadmap/V1.1-verification.md` — release evidence
- ADR-023 (SPI Stability Policy) — additive evolution rules
- ADR-018 (Unified Event Bus) — `KairoEventBus` is one of the passive consumption sites
- ADR-022 (KairoEvent OTel Exporter) — OTel sink is another passive consumption site
- ADR-025 (ExecutionSandbox SPI) — `SandboxRequest.tenant` is a `TenantContext`
- ADR-026 (Workspace SPI) — `WorkspaceRequest.tenant` is a `TenantContext`
- ADR-028 (Bridge Protocol) — `BridgeMeta.tenant` is a `TenantContext`
