# ADR-021 — Channel SPI (external system ↔ Agent) (v0.9)

## Status

Accepted — implemented in `v0.9.0` (SPI in `kairo-api`, reference impl + registry + TCK in `kairo-channel`, auto-config in `kairo-spring-boot-starter-channel`). Supersedes the ad-hoc per-adapter integration shape that used to live in application code.

## Context

Before v0.9, wiring DingTalk / 飞书 / Slack / a custom webhook to an Agent or an Expert Team was an application-level exercise: every adopter copied the same plumbing (inbound dispatch, outbound reply, identity plumbing, error surfacing) into their own Spring config. That is not a contract — just a pattern — so third parties could not publish a reusable adapter and Kairo could not assert any behavioral guarantee about it.

The v0.9 remaining-P0 scoping (`docs/roadmap/v0.9-remaining-p0-scoping.md`) locked D1 to **SPI + `LoopbackChannel` only** in v0.9, with real IM adapters shipping as separate v0.9.x modules that consume this SPI. This ADR freezes the SPI shape those adapters must target.

## Decision

Introduce `io.kairo.api.channel.*` as the stable contract for bidirectional integration between Kairo and an external system:

| Type | Role |
|---|---|
| `Channel` | Lifecycle + identity (`id()`, `start(handler)`, `stop()`, `sender()`) |
| `ChannelInboundHandler` | Application-side handler invoked when an external message arrives |
| `ChannelOutboundSender` | Adapter-side sender the runtime uses to push replies back |
| `ChannelMessage`, `ChannelIdentity` | Transport-agnostic envelope + addressable peer |
| `ChannelAck`, `ChannelFailureMode` | Uniform success/failure surface (adapters MUST NOT throw) |

Reference module `kairo-channel`:

- `LoopbackChannel` — in-memory implementation for tests and demos; captures outbound messages in an immutable log and exposes `simulateInbound` so tests can drive the handler without any transport.
- `ChannelRegistry` — `ConcurrentHashMap`-based lookup-by-id, throws on duplicate ids.
- `io.kairo.channel.tck.ChannelTCK` — abstract JUnit 5 contract kit with the three scenarios from the scoping doc:
  1. inbound message → handler → ack surfaced
  2. outbound transport failure → `ChannelFailureMode.SEND_FAILED`
  3. concurrent inbound messages preserve per-destination ordering

Starter `kairo-spring-boot-starter-channel`:

- Opt-in via `kairo.channel.enabled=true` (default off, matching the other v0.9 starters).
- When enabled, wires a shared `ChannelRegistry`. If a `ChannelInboundHandler` bean is present, `ChannelLifecycleManager` registers every discovered `Channel` bean and starts them (`kairo.channel.auto-start=true` by default).
- No handler → no lifecycle manager; the registry still exists so applications can wire lifecycle manually.

## Consequences

- **Pros**
  - Adapter authors get a single contract + TCK; the "is my DingTalk adapter correct?" question has a concrete answer.
  - The SPI stays transport-opinion-free: `ChannelIdentity` carries an opaque `attributes` map instead of forcing a common user model.
  - Deny-safe shape: no `ChannelInboundHandler` bean → no auto-started channels. Application owns the decision to wire a handler.
  - `ChannelAck` + `ChannelFailureMode` turn transport failures into a closed enum so observability dashboards can group failures across adapters consistently.
- **Cons**
  - No concrete IM adapter ships in v0.9 GA (by design, D1). Early users still need to wait for v0.9.x modules or write their own adapter against the SPI.
  - The SPI is `@Experimental` through v0.9 — per-destination ordering is a contract adapters owe but the TCK can only smoke-test it against loopback; real transports must extend the TCK to prove it at their own transport level.
- **Deferred to post-v0.9**
  - Persistent session binding for channel identities (need real-adapter usage feedback first).
  - Binary attachments / reactions / reply threading (adapters that need them stuff them into `attributes` meanwhile).
  - Per-destination rate limiting / backpressure primitives (out of scope until we see concrete adapter shapes).

## Non-goals (v0.9)

- Shipping any concrete IM adapter (DingTalk / 飞书 / Slack / webhook) inside this reactor — they ship as separate modules on the v0.9.x train.
- A domain user model layer on top of `ChannelIdentity` — applications that want one build it themselves.
- Retry / dead-letter semantics — outbound failure is surfaced; policy is the caller's choice.

## Post-v0.9 addendum (2026-04-24)

**v0.9.1 — DingTalk landed as the first concrete transport.** `kairo-channel-dingtalk` implements `Channel` on top of the custom-bot webhook API (HMAC-SHA256 signature verifier, JSON ↔ `ChannelMessage` mapper, JDK-`HttpClient` outbound). The Spring Boot starter `kairo-spring-boot-starter-channel-dingtalk` auto-wires a `DingTalkChannel` + `DingTalkWebhookController` at `/kairo/channel/dingtalk/callback`. The adapter extends `ChannelTCK` to prove baseline conformance and adds three DingTalk-specific scenarios (duplicate `msgId` dedup, signature-mismatch rejection, HTTP 429 / DingTalk `errcode 130101..130103` → `RATE_LIMITED`). See `docs/roadmap/v0.9.1-dingtalk-channel-verification.md` for evidence. No shape changes to the SPI were required to ship DingTalk — validation that the v0.9 contract is usable.
