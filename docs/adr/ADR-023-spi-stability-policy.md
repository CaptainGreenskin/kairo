# ADR-023 — SPI Stability Policy (v1.0)

## Status

Accepted — adopted in v1.0.0-RC1 (2026-04-24). Introduces `@Stable` + `@Internal` in
`io.kairo.api` alongside the existing `@Experimental`, and freezes the policy governing them.

## Context

Through v0.10.2 Kairo shipped 201 public types under `io.kairo.api.*` across 22 packages, of
which 54 carry `@Experimental`. The remaining 147 were unmarked — implicitly "probably stable
but never committed." That is not a public contract.

v1.0 is the OSS GA — the point where the framework promises to hold its shape for a major
version window. Without an explicit stability annotation, users have no mechanical signal
about what they can safely depend on, and maintainers have no compile-time enforcement that
they won't break that commitment.

The v0.10.2 SOT row and the `valiant-honking-coral` program plan both called out SPI
stabilization as the single largest v1.0 blocker. This ADR closes that gap.

## Decision

Introduce two new annotations in `kairo-api` alongside the existing `@Experimental`:

| Annotation | Guarantee | Breaking changes allowed in |
|------------|-----------|-----------------------------|
| `@Stable` | Shape frozen | Major versions only (v1.0 → v2.0) |
| `@Experimental` | Shape may change | Any minor version (v1.x → v1.y) |
| `@Internal` | No guarantee | Any patch version (v1.x.y → v1.x.z) |

Every type in `io.kairo.api.*` **must** carry exactly one of these three annotations by the
time v1.0.0 GA ships. The proposed triage (authoritative) lives in
`docs/governance/spi-census-v1.0.md` with a final split of:

- 119 types `@Stable` (62%)
- 70 types `@Experimental` (36%)
- 0 types `@Internal` (0% — the v0.10 cleanup waves already physically relocated genuinely
  internal types out of `io.kairo.api`)

Annotation targets: `TYPE`, `METHOD`, `FIELD`. Runtime retention so annotations survive
reflection-based surface extraction (e.g., for the japicmp gate in ADR-follow-up).

### Semantics of additive change

Stable types may evolve *additively* between minor versions without counting as a breaking
change:

- **Interfaces**: new `default` methods allowed; new abstract methods are breaking.
- **Records**: adding a new component IS breaking (canonical constructor change). Adding a
  static factory is additive.
- **Enums**: adding values AT THE TAIL is additive; reordering or removing values is
  breaking.
- **Classes**: adding new public methods / fields is additive; changing parameter types,
  return types, or method visibility is breaking.

### Downgrade rule

A `@Stable` element may never be downgraded to `@Experimental` or `@Internal` within a
major version. That would silently weaken a commitment users have already relied on.
Removal follows the standard deprecation cycle: `@Deprecated(forRemoval=true)` in vN.x,
remove in v(N+1).0.

### Application order

Phase 1 (this ADR): Annotate the 5 most-consumed SPIs as proof-of-mechanism — `Agent`,
`ModelProvider`, `ToolHandler`, `Msg`, `KairoException`. All annotated in the same PR as
this ADR.

Phase 2: Bulk annotate the remaining 114 `@Stable` types per the census. Tracked in
`docs/governance/spi-annotation-application.md`.

Phase 3: Backfill `@Experimental` on the 16 types in the census's experimental bucket that
aren't currently marked.

Phase 4: Wire `japicmp-maven-plugin` into `kairo-api` release build — `@Stable` surface
breaking changes fail the build (separate ADR-follow-up).

## Consequences

- **Pros**
  - Users see a compile-time signal for every API they touch. `@Stable` → depend freely.
    `@Experimental` → pin your version. `@Internal` → don't import.
  - Maintainers gain a commitment ledger. Any review that changes a `@Stable` signature is
    reviewable as a breaking-change decision, not an accident.
  - The japicmp gate (future) becomes mechanical: it only polices surfaces that carry the
    commitment. `@Experimental` changes pass silently — intentionally.
  - Publishing the census itself forces a triage discussion that was otherwise deferred
    indefinitely.
- **Cons**
  - Retrofitting annotations across 201 files is mechanical but not trivial. Split into
    8 per-package PRs (Phase 2).
  - 62 / 36 / 0 misses the original 70 / 20 / 10 target because four large subsystems
    (team, evolution, channel, guardrail) are not yet ready to freeze. This is a
    transparent trade — not a project failure — and the census records it as such.
- **Non-goals**
  - Runtime enforcement. These are metadata annotations for contract documentation + tooling
    input, not classloader-level gates.
  - Cross-module marking. `kairo-core` / `kairo-evolution` / etc. remain unannotated because
    those modules are not public contracts. Users consume them via the `kairo-api` surface.
  - Annotations on tests, generated code, or internal helper classes.

## Alternatives considered

1. **Package-level marker (`package-info.java` only)**: rejected — users browsing a single
   type in their IDE won't see a package-level annotation inline. Type-level is the minimum
   discoverable level.
2. **Stability in Javadoc only (`@apiNote Stable SPI...`)**: rejected — not enforceable,
   not parseable by tooling, easily drifts from reality.
3. **Stable-by-default / opt-out via `@Experimental`**: rejected — the status quo produced
   this problem. Explicit labeling forces the triage that implicit labeling hides.

## Related documents

- `docs/governance/spi-census-v1.0.md` — the authoritative type-by-type triage
- `docs/governance/spi-annotation-application.md` — execution ledger for Phases 1-3
- `kairo-api/src/main/java/io/kairo/api/Stable.java` — annotation source
- `kairo-api/src/main/java/io/kairo/api/Experimental.java` — annotation source (pre-existing)
- `kairo-api/src/main/java/io/kairo/api/Internal.java` — annotation source
- ADR-021 (Channel SPI) / ADR-016 (Coordinator SPI) / ADR-011 (Durable Execution) — examples
  of SPIs that will carry `@Experimental` through v1.0 and stabilize in v1.1 after real-world
  usage signal.
