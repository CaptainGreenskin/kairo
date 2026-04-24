# SPI Annotation Application Tracker — v1.0.0-RC1

**Companion to**: `docs/governance/spi-census-v1.0.md`

**Purpose**: Track progress applying `@Stable` / `@Experimental` / `@Internal` annotations to
the 201 types enumerated in the census. The census is the *decision*; this doc is the
*execution ledger*.

**Status**: In progress — Phase 1 (core SPIs) landed; Phase 2 (bulk annotation) pending.

---

## Phase 0 — Annotation source files (DONE, 2026-04-24)

| Annotation | File | Status |
|------------|------|--------|
| `@Experimental` | `kairo-api/.../Experimental.java` | Exists since v0.7 |
| `@Stable` | `kairo-api/.../Stable.java` | **New in v1.0** — created 2026-04-24 |
| `@Internal` | `kairo-api/.../Internal.java` | **New in v1.0** — created 2026-04-24 |

Verified: `mvn -pl kairo-api test` → 330 / 330 green.

---

## Phase 1 — Core SPI annotation (DONE, 2026-04-24)

Five most-consumed public SPIs stamped as proof-of-mechanism. These are the first surfaces
every Kairo user touches:

| Type | Annotation | Since | Rationale |
|------|------------|-------|-----------|
| `io.kairo.api.agent.Agent` | `@Stable` | 1.0.0 | Core ReAct contract; shape unchanged since v0.1 |
| `io.kairo.api.model.ModelProvider` | `@Stable` | 1.0.0 | Core model invocation contract |
| `io.kairo.api.tool.ToolHandler` | `@Stable` | 1.0.0 | Tool execution contract |
| `io.kairo.api.message.Msg` | `@Stable` | 1.0.0 | Core message type |
| `io.kairo.api.exception.KairoException` | `@Stable` | 1.0.0 | Base exception + structured fields |

Verified: annotations applied, spotless green, `mvn -pl kairo-api test` green (330 / 330).

---

## Phase 2 — Bulk @Stable annotation (PENDING)

Remaining 114 types in the census's `@Stable` bucket. Grouped by package for batch PRs:

| Package | Types to annotate | Est. PRs |
|---------|-------------------|---------:|
| `agent` (remaining) | `AgentConfig`, `AgentFactory`, `AgentState`, `AgentSnapshot`, `SnapshotStore`, `CancellationSignal` (6) | 1 |
| `context` | 12 (entire package) | 1 |
| `exception` (remaining) | 10 | 1 |
| `execution` | 9 | 1 |
| `hook` | 26 | 1 |
| `mcp` | 3 | 1 (combined w/ memory) |
| `memory` | 5 | |
| `message` (remaining) | `MsgRole`, `Content` (2) | 1 (combined w/ plan) |
| `model` (remaining) | 11 | 1 |
| `plan` | 2 | |
| `skill` | `SkillCategory`, `SkillDefinition`, `SkillRegistry`, `TriggerGuard` (4) | 1 (combined w/ tracing) |
| `tool` (remaining) | 20 | 1 |
| `tracing` | 4 | |

**Total**: ~8 PRs or one mega-PR. Recommend one PR per logical group for reviewability.

**Mechanics** (for automation):

```
# Add import
import io.kairo.api.Stable;

# Stamp type declaration
@Stable(value = "<rationale from census>", since = "1.0.0")
public interface|class|record|enum ...
```

Run `mvn -pl kairo-api spotless:apply` after each edit; verify `mvn -pl kairo-api test` green.

---

## Phase 3 — @Experimental backfill audit (PENDING)

The census assigns 70 types to `@Experimental`. Cross-reference the existing `rg "@Experimental"`
census (54 files currently marked) against the target list to identify gaps:

| Census target | Currently marked | Gap |
|---------------|-----------------:|----:|
| 70 | 54 | **16 types need `@Experimental` added** |

Gap analysis (pending):

- a2a (6) — 0 currently marked → 6 new annotations
- middleware (5) — 0 currently marked → 5 new annotations
- Remaining: 5 gaps across team/evolution/event packages where individual types slipped through

Phase 3 may overlap with Phase 2 depending on review cadence.

---

## Phase 4 — Enforcement (SEPARATE DELIVERABLE — B3A.2)

`japicmp-maven-plugin` wired into `kairo-api` release build. `@Stable`-surface breaking
changes fail the build. See `docs/governance/japicmp-policy.md` (to be written).

---

## Non-goals

- Retrofitting annotations to `kairo-core` internal types — `kairo-core` is the runtime, not
  the contract.
- Annotating generated code (Jackson mixins, Spring-generated AOT hints, etc.).
- Annotating test-only types.

---

## Open questions / decisions

1. **Methods or just types?** — Current policy: annotate type level only. Method-level
   `@Stable` / `@Experimental` is allowed by the annotation target but will only be used
   for exceptional cases (e.g., a stable interface gains an experimental default method).
2. **Records — does `@Stable` apply to accessors?** — Yes, implicitly. The record's
   canonical components are part of the stable surface. Adding new components is a breaking
   change and requires major version bump.
3. **Enum values** — `@Stable` on an enum freezes *existing* values. New values may be
   added at the tail (follows JEP 406 / sealed hierarchy convention).

---

## Related documents

- `docs/governance/spi-census-v1.0.md` — authoritative triage (input)
- `.plans/VERSION-STATUS-SOT.md` — v1.0.0 row
- `docs/roadmap/v1.0.0-rc1-spi-stabilization.md` — Wave 3A verification doc (to be written at RC1 cut)
