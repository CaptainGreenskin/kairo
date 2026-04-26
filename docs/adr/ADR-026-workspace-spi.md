# ADR-026 — Workspace SPI (v1.1)

## Status

Accepted — implemented in `v1.1.0` (SPI in `kairo-api/.../workspace/`, `LocalDirectoryWorkspaceProvider` default in `kairo-core`, file-tool refactor + per-tool relative-path regression case). Replaces the implicit "working directory = `System.getProperty("user.dir")`" assumption baked into every file tool through v1.0.

## Context

Through v1.0, the working directory observed by a tool was whatever the JVM's process cwd happened to be. That implicit ambient state breaks the moment a single agent must operate against more than one workspace concurrently — the canonical example being "review five PRs at once, each backed by its own checkout."

There was no SPI to plug a remote git checkout (S3-overlay, JGit-managed working tree) either, because the assumption "the workspace is on the local filesystem at `user.dir`" was hardcoded.

`.plans/V1.1-SPI-FOUNDATIONS.md` F2 calls this the second of v1.1's four walls. The fix is to elevate "workspace" from ambient state to an explicit context object passed down via `ToolContext` — without changing the default behavior for callers that don't multi-workspace.

## Decision

Introduce `io.kairo.api.workspace.*` as the stable contract for workspace context:

| Type | Role |
|---|---|
| `Workspace` | `id()` / `root()` / `kind()` / `metadata()` — the handle a tool consumes |
| `WorkspaceProvider` | `acquire(WorkspaceRequest)` / `release(String workspaceId)` — materialises a workspace |
| `WorkspaceRequest` (record) | `hint` (provider-specific identifier) / `tenant` / `writable` |
| `WorkspaceKind` (enum) | `LOCAL` (v1.1 ships) / `REMOTE_GIT` / `EPHEMERAL` (reserved for v1.3) |
| `Workspace.cwd()` static factory | Default no-dependency `Workspace` rooted at `Path.of("").toAbsolutePath()` |

Default implementation `LocalDirectoryWorkspaceProvider` (in `kairo-core`):

- `acquire(req)` resolves `req.hint()` as a directory path; null hint → cwd.
- Returns the same `Workspace` instance for the same hint (idempotent for LOCAL).
- `release(id)` is a no-op for LOCAL — local directories don't need teardown.

`ToolContext` integration:

- `ToolContext` exposes `currentWorkspace()` returning the active `Workspace`. When unbound, it returns `Workspace.cwd()` so existing callers observe zero behavior change.
- File tools (`Read` / `Write` / `Edit` / `Glob` / `Grep`) resolve relative input paths against `currentWorkspace().root()`. Absolute paths are honoured verbatim.
- `BashTool` constructs its `SandboxRequest.workspaceRoot()` from `currentWorkspace().root()` (see ADR-025).

### Relative-path resolution rule

A path argument supplied to a file tool is resolved as follows:

1. If `Path.isAbsolute(arg)` → use verbatim. No workspace prefix is applied.
2. Else → resolve against `currentWorkspace().root()` via `root.resolve(arg).normalize()`.
3. The resolved path MUST remain inside `root` after normalisation (`startsWith(root)` check). Paths that escape via `..` are rejected with an `IllegalArgumentException`. This is a defence-in-depth guard, not the only one — `Workspace` implementations may add stronger sandboxing.

This rule is consistent across all five file tools and is exercised by a per-tool regression test that asserts a relative path resolves to `root.resolve(arg)`, not `Path.of(arg)`.

## Consequences

- **Pros**
  - A single agent process can manage multiple workspaces concurrently — five-PR-review now works without thread-locals or argument plumbing.
  - The SPI is backend-agnostic: `WorkspaceKind` reserves `REMOTE_GIT` for remote-checkout providers and `EPHEMERAL` for sandbox-backed workspaces, so v1.3 can ship those without breaking v1.1 contracts.
  - Existing single-workspace callers observe **zero behaviour change**: `Workspace.cwd()` returns a `LOCAL` workspace rooted at `user.dir`, which is exactly what file tools used before.
  - Path-traversal guard via `startsWith(root)` after `normalize()` is enforced at the SPI boundary, not per-tool — every file tool inherits the same defence.
- **Cons**
  - Five file tools were refactored. Mitigation: per-tool relative-path regression case + the default workspace = cwd ensures regressions are caught and contained. Risk noted in plan §"风险与对策".
  - `WorkspaceProvider.acquire()` returns `RuntimeException` on resolution failure, not a checked exception. Callers MUST treat this as a non-recoverable error and surface the message; we do not add fine-grained typed failures until real-world adapter signal arrives.
- **Deferred to post-v1.1**
  - `RemoteGitWorkspaceProvider` — JGit / GitHub API backed checkout, materialises `Workspace.kind() == REMOTE_GIT`. v1.3.
  - `EphemeralWorkspaceProvider` — sandbox-mounted scratch directories with explicit `release()` semantics. v1.3.
  - S3-overlay workspaces — orthogonal v1.3 design.
  - Workspace lifecycle events on `KairoEventBus` — when a workspace is acquired / released. Hold until real provider use cases land.

## Non-goals (v1.1)

- Shipping `REMOTE_GIT` or `EPHEMERAL` provider implementations — the enum values are reserved as contract anchors so v1.3 can land them additively.
- Workspace permissions / ACLs — `WorkspaceRequest.writable()` is advisory in v1.1; providers MAY enforce read-only mounts but are not required to.
- Tenant-scoped workspace registries — multi-tenant isolation is v1.2 (see ADR-027 §non-goals).
- Per-workspace event-bus subscription — events that mention a workspace just attach the id as an attribute, no routing layer.

## Future-extension rules

For a future provider to land cleanly:

1. It MUST handle `WorkspaceRequest.hint()` according to its backend convention. Document the format on the provider Javadoc.
2. It MUST return a `Workspace` whose `root()` is valid until `release(id)` is called. For ephemeral backends, that lifetime SHOULD be longer than a single tool call to amortise setup cost across an agent run.
3. It MUST be safe for concurrent `acquire()` invocations.
4. Adding a new `WorkspaceKind` enum value requires the additive rule from ADR-023 §"Semantics of additive change" — append at the tail, never reorder.
5. The `Workspace.metadata()` map is the extension point for backend-specific labels (e.g., `git.remote`, `git.branch`, `git.commit`). Standardised keys SHOULD be documented in `WorkspaceProvider` Javadoc; ad-hoc keys are allowed.

## Related documents

- `.plans/V1.1-SPI-FOUNDATIONS.md` — F2 spec + risk register
- `kairo-api/src/main/java/io/kairo/api/workspace/package-info.java` — package-level Javadoc
- `docs/roadmap/V1.1-verification.md` — release evidence
- ADR-023 (SPI Stability Policy) — additive evolution rules
- ADR-025 (ExecutionSandbox SPI) — `SandboxRequest.workspaceRoot` is a `Workspace.root()` value
- ADR-027 (TenantContext) — `WorkspaceRequest.tenant` carries the active tenant
