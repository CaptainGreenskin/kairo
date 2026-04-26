# ADR-020 — Skill subsystem unification (`SkillStore` SPI) (v0.10)

## Status

Accepted — **SPI introduced** in `v0.10.0`; physical module split + store implementations are follow-up.

## Context

Skills exist in multiple shapes today:

- static skills (`SkillDefinition` + `SkillRegistry`)
- evolved skills (`EvolvedSkill` + `EvolvedSkillStore`)

This split makes “one skill lifecycle” harder to reason about for observability, policy, and persistence.

## Decision

Add a minimal reactive `SkillStore` SPI in `kairo-api`:

- `save/get/delete/list` only (SPI Earned: no speculative operations)

Treat this as the **convergence target** for:

- static skill persistence adapters, and
- evolved skill persistence adapters

## Consequences

- **Pros**: a single cross-cutting persistence abstraction for future governance/audit/OTel.
- **Cons**: requires a migration period; `SkillRegistry` remains until adapters exist.

## Follow-ups

- Add `kairo-skill` module and relocate loaders/registry/tooling from `kairo-core` / `kairo-tools`.
- Provide default `SkillStore` implementation(s) and wire Spring auto-configuration.
