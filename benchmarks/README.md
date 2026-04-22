# Kairo Benchmarks (Lightweight)

This folder provides a lightweight, repeatable benchmark baseline to validate Kairo agent capabilities before `v1.0`.

## Purpose

The goal is not to "win a leaderboard", but to publish reproducible evidence for:

- tool-call correctness
- long-task stability
- safety governance effectiveness
- cost and latency trends between releases

## Folder Structure

- `scenarios/v0-lite-scenarios.jsonl`: initial scenario set (small but representative)
- `metrics-schema.json`: canonical result fields for benchmark output

## How To Run (Current Minimal Flow)

1. Start from a clean branch and a fixed model/provider configuration.
2. Execute scenarios in `scenarios/v0-lite-scenarios.jsonl` using your runner.
3. Emit one JSON record per scenario using `metrics-schema.json`.
4. Aggregate:
   - success rate
   - safety block rate
   - median latency
   - mean token/cost per successful task

## Output Contract (Required)

Each record SHOULD include:

- scenario id and category
- release tag / git commit
- model/provider info
- pass/fail and failure reason
- latency and token usage
- guardrail/security decisions

## Scope

This is the **lightweight proof track** for v0.7-v0.9.
The full benchmark report (cross-version trend + baseline comparisons) will be consolidated for `v1.0`.

