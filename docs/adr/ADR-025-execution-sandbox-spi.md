# ADR-025 — ExecutionSandbox SPI (v1.1)

## Status

Accepted — implemented in `v1.1.0` (SPI in `kairo-api/.../sandbox/`, default `LocalProcessSandbox` in `kairo-tools`, `BashTool` refactor + `ExecutionSandboxTCK`). Replaces the hard-coded `ProcessBuilder` call previously embedded in `BashTool.java`.

## Context

Through v1.0 GA, `BashTool` instantiated `java.lang.ProcessBuilder` directly:

```java
// kairo-tools/.../BashTool.java (v1.0)
Process p = new ProcessBuilder("/bin/sh", "-c", command).start();
```

That worked for the single-process / single-host / single-tenant target v1.0 was scoped to. It does not survive the moment Kairo grows past one of those four constraints:

- A **container or micro-VM backend** (`DockerSandbox`, `FirecrackerSandbox`) can't be plugged in without forking `BashTool`.
- A **remote execution backend** (`RemoteHttpSandbox` for cloud agent runtimes) can't be plugged in.
- A **multi-tenant deployment** has no seam to attribute commands to a tenant for quota / cost / audit.

The companion document `.plans/V1.1-SPI-FOUNDATIONS.md` calls out four "walls" v1.1 must scale; this is the first. The constraint is the SPI seam, not the local default — `LocalProcessSandbox` continues to be the bundled implementation and stays byte-for-byte compatible with v1.0 behavior.

## Decision

Introduce `io.kairo.api.sandbox.*` as the stable contract for executing untrusted shell commands on behalf of a tool:

| Type | Role |
|---|---|
| `ExecutionSandbox` | Single-method SPI — `SandboxHandle start(SandboxRequest)` |
| `SandboxRequest` (record) | Immutable execution descriptor: `command`, `workspaceRoot`, `env`, `timeout`, `maxOutputBytes`, `tenant`, `readOnly` |
| `SandboxHandle` (interface, `AutoCloseable`) | Live handle: hot `Flux<SandboxOutputChunk> output()`, cached `Mono<SandboxExit> exit()`, idempotent `cancel()` / `close()` |
| `SandboxOutputChunk` (sealed) | `Stdout(byte[])` / `Stderr(byte[])` discriminator preserves stream identity |
| `SandboxExit` (record) | `exitCode` / `signal` / `timedOut` / `truncated` |

Default implementation `LocalProcessSandbox` (in `kairo-tools`):

- Wraps `ProcessBuilder` exactly as the v1.0 `BashTool` did.
- Owns the behavior contract: timeout watchdog + `kill -9` on expiry, `maxOutputBytes` truncation with the `truncated` flag set, working-directory selection from `SandboxRequest.workspaceRoot()`, environment delta application, `readOnly` enforcement (refuses writes to `workspaceRoot` when set).
- Multicasts stdout/stderr via `Sinks.Many.multicast().onBackpressureBuffer()` so subscribers join late without a replay storm.

`BashTool` refactor:

- Resolves the active sandbox from `ToolContext` first; falls back to `LocalProcessSandbox` when none is bound. Existing single-process call sites observe zero behavior change.
- Drops its inline `ProcessBuilder`. Drains the hot `Flux<SandboxOutputChunk>` into a single string for backward compatibility with the existing return shape.
- Public method signatures unchanged.

`ExecutionSandboxTCK` (`kairo-tools/src/main/java/io/kairo/tools/sandbox/tck/`) is an abstract JUnit 5 contract kit any backend must pass. Scenarios:

1. Successful exit (exit code 0, full output captured).
2. Non-zero exit code surfaced verbatim.
3. Timeout fires → process killed → `exit().timedOut() == true` and `exit().exitCode() == -1`.
4. Output exceeding `maxOutputBytes` is truncated with `truncated == true`.
5. `cancel()` is idempotent and surfaces as a backend-specific signal.
6. `readOnly == true` rejects write attempts to `workspaceRoot`.
7. Concurrent `start()` calls are isolated (no shared state leak).
8. `close()` releases backend resources and is idempotent.

## Consequences

- **Pros**
  - Single contract + TCK gives backend authors ("is my Docker sandbox correct?") a concrete answer.
  - Behavior contract sits on the sandbox, not the tool — every backend honours timeout / truncation / readOnly identically by passing the TCK.
  - Streaming output is now first-class: future tools can subscribe directly to `Flux<SandboxOutputChunk>` for progressive UI feedback. v1.0 callers still get the batched-string shape via `BashTool`.
  - Tenant attribution flows through the `SandboxRequest.tenant()` field — backends can label container runs / quota counters / cost rollups by tenant without changing tool code.
- **Cons**
  - The bundled `LocalProcessSandbox` lives in `kairo-tools`, not `kairo-api`. Pure-API consumers depending only on `kairo-api` see the SPI but no default — they must either ship their own backend or pull `kairo-tools`. This matches the existing `Channel` / `WorkspaceProvider` shape and is intentional.
  - Behavior contract is documented + TCK-enforced but cannot be JLS-enforced. Backends that ignore the truncation rule will fail the TCK, not the compile.
- **Deferred to post-v1.1**
  - `DockerSandbox` (container isolation) — v1.2.
  - `FirecrackerSandbox` (lightweight micro-VM, multi-tenant cloud) — v1.2.
  - `RemoteHttpSandbox` (gRPC / HTTP execution backend) — v1.2.
  - Per-sandbox quota / rate-limit hooks — v1.2 alongside `TenantContext` quota enforcement (ADR-027 §non-goals).

## Non-goals (v1.1)

- Shipping any non-local backend inside this reactor — only the `LocalProcessSandbox` default ships in v1.1, exactly as v1.0 already did.
- Changing `BashTool`'s public method signatures or return shape — refactor is internal.
- Process / tool sandboxing beyond shell execution — `Read` / `Write` / `Edit` etc. continue to use direct filesystem APIs scoped by `Workspace.root()` (see ADR-026); we do not route every tool through the sandbox.
- Network egress filtering — that's a v1.2+ concern when remote backends land.

## Future-extension rules

When a non-local backend ships:

1. It MUST extend `ExecutionSandboxTCK` and pass all eight scenarios.
2. It MUST honour `SandboxRequest.tenant()` for whatever attribution / isolation the backend supports.
3. It MAY add backend-specific options via attributes on a future `SandboxRequest.attributes()` field — additive only, never via constructor change.
4. It MUST NOT rely on `LocalProcessSandbox`-private classes (e.g., the watchdog impl).

The `SandboxOutputChunk` sealed interface is intentionally open for `permits` extension to add e.g. `Stdin(byte[])` for interactive sandboxes — adding a permit is a binary break and requires a major version (or a new sibling type without changing the sealed list, per ADR-023 §"Semantics of additive change").

## Related documents

- `.plans/V1.1-SPI-FOUNDATIONS.md` — F1 spec + execution order
- `kairo-api/src/main/java/io/kairo/api/sandbox/package-info.java` — package-level Javadoc
- `docs/roadmap/V1.1-verification.md` — release evidence
- ADR-023 (SPI Stability Policy) — `@Stable` retention rules
- ADR-026 (WorkspaceSPI) — `workspaceRoot` is a `Workspace.root()` value
- ADR-027 (TenantContext) — `SandboxRequest.tenant` is the propagated `TenantContext`
