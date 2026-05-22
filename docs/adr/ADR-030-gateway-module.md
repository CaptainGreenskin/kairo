# ADR-030: Kairo Gateway Module (above Channel SPI)

* Status: Accepted
* Date: 2026-05-22
* Stakeholders: Kairo core, kairo-assistant, kairo-code (future)
* Supersedes / refines: ADR-018 (Channel SPI), borrows from hermes-agent's gateway

## Context

Kairo's `kairo-channel` SPI (v0.9 `@Experimental`) is intentionally a thin transport
abstraction: an adapter implements `Channel.start/stop/sender`, emits text-only
`ChannelMessage` envelopes, and the application owns the inbound handler. That's the right
shape for webhooks and simple programmatic channels.

But every real IM integration in our ecosystem (DingTalk, Feishu, the assistant's chat
WebSocket) reimplements the same higher-layer plumbing on top:

- session ID derivation from `(channel, chat, user, thread)` so consecutive turns join
- token-by-token streaming via repeated `edit_message` calls (rate-limited, debounced)
- platform-specific media uploads (image / voice / document)
- delivery routing (`"telegram:12345"` form for cron jobs, `send_message` tools)
- per-channel slash commands (`/reset`, `/help`, `/stop`)
- mirroring inbound + outbound to disk for incident replay
- cross-channel user pairing (same human reaching the agent from two platforms)

`hermes-agent/gateway/` solves exactly this in Python — 26K LOC of orchestration over 25+
platforms, with a `BasePlatformAdapter` that carries 50+ methods. Bringing every one of
those methods up to `kairo-api` would bloat the SPI past its `@Stable` ergonomics.

## Decision

Add a **new `kairo-gateway` module** under `kairo-capabilities/`, layered **above**
`kairo-channel`. Keep `Channel` thin and unchanged; gateway introduces a richer adapter
contract (`GatewayPlatformAdapter`) and the surrounding orchestration.

### Module shape

```
kairo-api/io/kairo/api/gateway/   ← SPI (10 interfaces / records, all @Experimental)
  Gateway                          ← top-level: register adapters, fan-in, deliver
  GatewayPlatformAdapter           ← extends Channel concept with media/edit/draft/typing
  PlatformCapabilities             ← declared optional feature flags
  RichChannelMessage               ← message DTO with type + attachments + reply context
  Attachment                       ← media envelope
  MessageType                      ← TEXT / IMAGE / VIDEO / AUDIO / VOICE / DOCUMENT / …
  DeliveryTarget                   ← origin / local / channel[:chat[:thread]] parse + DTO
  SessionSource                    ← (channelId, chatId, userId, threadId, chatType)
  SendResult                       ← success/messageId/failureMode

kairo-gateway/                     ← implementation
  DefaultGateway                   ← lifecycle + fan-in + DeliveryRouter wiring
  GatewayBuilder                   ← fluent build
  routing/DeliveryRouter           ← target → adapter dispatch, error → SendResult.fail
  session/SessionDirectory         ← (channel,chat,thread) → session id, in-memory
  session/PairingStore             ← (channel,userId) → kairoUserId, JSON-on-disk
  mirror/MirrorStore               ← interface
  mirror/JsonlMirrorStore          ← append-only NDJSON
  stream/StreamConsumer            ← Flux<String> tokens → send + repeated edit/draft
  cmd/SlashCommandRegistry         ← cross-channel /command dispatch
  hooks/GatewayHookRegistry        ← inbound/outbound/result hooks
  tck/GatewayAdapterTCK            ← conformance kit for third-party adapters

kairo-spring-boot-starter-gateway/ ← @AutoConfiguration that picks up bean-registered
                                     adapters + wires Gateway/SessionDirectory/MirrorStore
```

### What stays untouched

`kairo-channel`, `kairo-channel-dingtalk`, `LoopbackChannel`, `ChannelTCK`, and
`AssistantWebSocketHandler` in kairo-assistant are unchanged in this phase. Anyone using
`Channel` directly keeps working — the gateway module is purely additive.

### Why a separate module instead of expanding Channel

1. **Scope discipline.** `Channel` is the transport contract; growing it with media /
   edit / draft / pairing methods turns it into a 30-method God interface (see hermes
   `BasePlatformAdapter`). Keeping orchestration separate lets `Channel` stay easy to
   implement for simple webhook-only use cases.
2. **Capability negotiation.** `GatewayPlatformAdapter.capabilities()` lets the gateway
   degrade gracefully — fall back from `sendDraft` to `editMessage` to plain `send` based
   on what each platform actually supports. Same code path can serve a Telegram adapter
   with full streaming and an SMS adapter with text-only.
3. **TCK fit.** `GatewayAdapterTCK` is a stricter contract (returns SendResult on every
   send, no exceptions across SPI, declared capabilities consistent) than `ChannelTCK`.
   Mixing both contracts in one TCK would force users of the simpler Channel SPI to pass
   tests they don't care about.

### What the gateway gives applications

A single `Gateway` bean fanned in from N adapters:

```java
@Autowired Gateway gateway;

Flux<RichChannelMessage> messages = gateway.inbound();
gateway.deliver("telegram:12345", "hello").subscribe();
```

…plus a `StreamConsumer` bridge that turns agent token output into native streaming
messages:

```java
agent.stream(input)
     .flatMap(tokens -> streamConsumer.consume(adapter, target, tokens))
     .subscribe();
```

No more per-platform copies of the same edit-message debounce loop.

## Consequences

* **kairo-assistant migration (deferred Phase 3).** `DingTalkStreamRunner`,
  `FeishuStreamRunner`, and the chat WebSocket handler become `GatewayPlatformAdapter`
  implementations in followup PRs. The assistant subscribes to `Gateway.inbound()`
  instead of running per-channel runners.
* **DingTalk gateway adapter (Phase 4).** `kairo-channel-dingtalk` gains an optional
  `GatewayPlatformAdapter` facade so DingTalk can be consumed either via the thin
  `Channel` interface (current) or the rich gateway API (new).
* **No SPI break.** All gateway SPI is `@Experimental`. `Channel` is unchanged.
* **One new BOM entry, one new starter.** `kairo-gateway` + `kairo-spring-boot-starter-gateway`
  added to the BOM at `${revision}`.

## Alternatives considered

1. **Bloat `Channel` SPI.** Rejected: turns it into `BasePlatformAdapter`-class God
   interface; punishes simple webhook adapters with 30+ methods they'll never implement.
2. **Build inside kairo-assistant.** Rejected: framework concern, not application
   concern. Hermes-agent makes this clear — every Hermes application would re-import the
   same gateway. Goes in the framework so kairo-code and future agents reuse it.
3. **Skip and keep duplicating per-channel.** Rejected: every new platform integration
   would re-walk the same rate-limit / edit-debounce / session-routing bugs.

## Validation

* 66 unit tests in `kairo-gateway` (`DefaultGatewayTest`, `DeliveryRouterTest`,
  `SessionDirectoryTest`, `StreamConsumerTest`, `JsonlMirrorStoreTest`,
  `PairingStoreTest`, `SlashCommandRegistryTest`, `HookRegistryTest`, `DeliveryTargetTest`,
  `AttachmentTest`, plus the TCK self-test `FakeAdapterTCKTest`)
* 4 starter tests verifying auto-config wiring + property toggling
* All kairo-api / kairo-channel tests still green (no SPI break)
