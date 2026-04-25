# ADR-028 — Bridge Protocol (v1.1)

## Status

Accepted — implemented in `v1.1.0` (SPI in `kairo-api/.../bridge/`, default `WebSocketBridgeServer` + `KairoBridgeWebSocketHandler` in `kairo-event-stream-ws`, 5 op integration tests + 3 error-path tests). Closes the gap left by the v0.9 unidirectional event-stream SSE / WS transports — those are server → client only and have no surface for "client → server" command / status / approval calls.

## Context

Through v1.0, Kairo had:

- **Server → client** event delivery via `kairo-event-stream-sse` (HTTP SSE) and `kairo-event-stream-ws` (Spring WebFlux reactive WS) — both downstream-only.
- **Synchronous tool calls** via the standard model-provider tool-use loop (single agent invocation, single response).

What it lacked: a way for an external companion client (TUI / Web / IDE plugin) to **drive** the agent runtime — `agent.run`, `agent.cancel`, `agent.status`, `tool.approve` (for risky tool gating), `workspace.list`. AgentCode (line B) cannot ship its TUI / Web / IDE three-pane UX without this.

`.plans/V1.1-SPI-FOUNDATIONS.md` F4 scopes the fix to a **minimal bidirectional RPC**: an envelope-based op dispatcher riding the existing WS transport, with a frozen 5-op catalog and a schemaless payload so future ops can be added without bumping a protocol version.

## Decision

Introduce `io.kairo.api.bridge.*` as the stable contract for client → agent runtime calls:

| Type | Role |
|---|---|
| `BridgeRequest` (record) | `op` (String) / `payload` (`Map<String,Object>`) / `meta` (`BridgeMeta`) — the inbound envelope |
| `BridgeResponse` (record) | `status` (HTTP-style int) / `payload` (`Map<String,Object>`) — the outbound envelope |
| `BridgeMeta` (record) | `requestId` / `tenant` (`TenantContext`) / `timestamp` / `attributes` |
| `BridgeRequestHandler` | `Mono<BridgeResponse> handle(BridgeRequest)` — the dispatcher SPI applications implement |
| `BridgeServer` | `start()` / `stop()` / `endpoint()` — the transport lifecycle SPI |

Default transport `WebSocketBridgeServer` (in `kairo-event-stream-ws`):

- Owns a `KairoBridgeWebSocketHandler` mounted on its own URL pattern (typically `/ws/bridge`) — separate from the event-stream WS endpoint so the two channels do not multiplex onto the same session.
- Decodes inbound text frames as JSON envelopes via Jackson, dispatches to the `BridgeRequestHandler`, and writes correlated response frames.
- Lifecycle gate: a shared `AtomicBoolean running` flag is checked on each new session. When `stop()` flips it false, new sessions are closed with custom WS `CloseStatus(4503, "bridge-stopped")`. In-flight sessions complete naturally.

### Wire format

Inbound text frame (UTF-8 JSON):

```json
{
  "requestId": "req-1",
  "op": "agent.run",
  "payload": { "agent": "summarizer", "input": "hello" },
  "meta": { "sessionId": "s-1" }
}
```

Outbound text frame:

```json
{
  "requestId": "req-1",
  "status": 200,
  "payload": { "runId": "run-42", "status": "started" }
}
```

`requestId` is supplied by the client; if absent, the server **synthesizes a UUID and reflects it on the response** so the client can still correlate. Malformed JSON resolves to a `400` response (with a synthesized requestId), unknown ops to `404`, handler exceptions / null returns to `500`. The session itself is **never closed on application-level errors** — only transport-level failures and explicit shutdown drop the connection.

### Required v1.1 op catalog

| op | Inbound payload | Outbound payload | Purpose |
|---|---|---|---|
| `agent.run` | `{ agent, input, ... }` | `{ runId, status }` | Kick off an agent invocation |
| `agent.cancel` | `{ runId }` | `{ cancelled }` | Terminate the active invocation |
| `agent.status` | `{ runId }` | `{ runId, phase }` | Query session state |
| `tool.approve` | `{ toolCallId, decision, reason }` | `{ acked }` | Accept / reject a risky tool execution (feeds `UserApprovalHandler`) |
| `workspace.list` | `{}` | `{ workspaces: [{id, kind, ...}, ...] }` | Enumerate workspaces visible to the principal |

The **envelope shape** (`requestId` / `op` / `payload` / `meta`) is frozen by this ADR. The **op catalog is open for extension** — adding a new op is *not* a protocol-version bump (no version field exists by design). However, the **semantics of an existing op MAY NOT change once published**. Implementations encountering an unknown op MUST return a `404`; consumers that depend on a particular op MUST be tolerant of `404` responses from older servers.

### Plan deviation: `Map<String, Object>` instead of `JsonNode`

`.plans/V1.1-SPI-FOUNDATIONS.md` F4 sketched the payload type as `JsonNode`. The implementation uses `Map<String, Object>` instead. Rationale:

- `kairo-api` is a **dependency-minimal pure-interface module**. The existing rule (per `package-info` of `kairo-api`) is "Pure interfaces for the agent runtime (no implementation dependencies)." Pulling Jackson into `kairo-api` violates that rule; every adopter would inherit Jackson on the API surface even if they wanted Gson, Moshi, or a custom decoder.
- The codebase already establishes `Map<String, Object>` as the schemaless attribute precedent: `KairoEvent.attributes`, `BridgeMeta.attributes`, `Workspace.metadata`, `ChannelMessage` payloads — all use the same shape.
- Schemaless intent is preserved: the bridge does not freeze any payload schema, only the envelope.
- The `WebSocketBridgeServer` default impl converts to/from `JsonNode` internally via Jackson on the wire, but the SPI surface stays Jackson-free.

The `JsonNode` shape would have produced a `kairo-api → jackson-databind` compile dependency; the `Map<String, Object>` shape leaves `kairo-api` clean. This is the same trade made for `KairoEvent.attributes` and is consistent with ADR-023 §"Stable types must depend only on JDK / Reactor / kairo-api itself" guidance.

## Consequences

- **Pros**
  - Companion clients (AgentCode TUI / Web / IDE plugin) get a single contract to drive the same agent runtime — three-end UX falls out of one SPI.
  - The schemaless envelope means new ops land additively across all three clients without coordinating a protocol-version bump.
  - HTTP-style status codes (`200`/`400`/`404`/`500`) give observability dashboards a familiar grouping primitive — bridge errors slot into the same alerting model as REST errors.
  - Application-level errors (`400` / `404` / `500`) **never close the session**. A misbehaving client can spam malformed frames and the server keeps serving the well-formed ones.
  - Riding the existing `kairo-event-stream-ws` transport stack means no new TCP port, no new auth seam, no new TLS config — the bridge inherits whatever Spring Security upstream already enforces on `/ws/*`.
- **Cons**
  - Schemaless-by-design means we can't compile-check op payloads. Mitigation: each op is integration-tested against its expected payload shape (8 tests in `KairoBridgeWebSocketHandlerTest`); op authors document their payload contract on the `BridgeRequestHandler` impl Javadoc.
  - `Map<String, Object>` payloads force application code to cast / pluck values manually instead of binding to a typed record. This is the same trade-off `KairoEvent.attributes` already made — the cost is small and concentrated in op handlers, where typed wrapper classes can be added per-application.
  - The 5-op catalog is **frozen by Javadoc / ADR**, not by the type system — there's no `enum BridgeOp` because that would force an enum-bump for every new op. Adopters discover the catalog from the `package-info` Javadoc.
- **Deferred to post-v1.1**
  - **File transfer / large-payload streaming** — `BridgeRequest.payload` is bounded by JSON encoding cost. Anything > a few hundred KB needs a separate channel. v1.3.
  - **Server-pushed bridge frames** — currently the bridge is request/response only. Server-to-client push continues to use `KairoEventBus` event-stream. If genuine RPC-style server-push lands, it adds a sibling op type rather than mutating the existing envelope.
  - **Protocol-version negotiation** — intentionally absent. Adding a `version` field would invite the bikeshed without solving any concrete problem. Apps that need versioning encode it in `op` (e.g. `agent.run.v2`).
  - **Transport-level authentication** — Spring Security upstream owns it. The bridge handler runs after auth has already happened and trusts `TenantContext.current()`.

## Non-goals (v1.1)

- No file transfer protocol — large payloads stay out of band; v1.3 adds a sibling channel if needed.
- No protocol-version field — schemaless envelope means version is unnecessary.
- No transport-level auth — Spring Security upstream is authoritative.
- No server-push of bridge frames — `KairoEventBus` covers that already.
- No structured op catalog enum — frozen by ADR / Javadoc, not by Java types.

## Future-extension rules

When a new op lands:

1. Document its payload shape on the relevant `BridgeRequestHandler` Javadoc + this ADR's op catalog.
2. Choose a stable `op` string of the form `<namespace>.<verb>` (e.g. `tool.approve`, `workspace.list`). Reuse existing namespaces where the new op fits.
3. Servers encountering an unknown op MUST return `BridgeResponse.notFound(op)`. Clients MUST treat `404` as "this server doesn't know this op" — *not* as a protocol error.
4. Once an op is published with semantics X, it MAY NOT change semantics. New behaviour ships as a new op (e.g. `agent.run.v2`).
5. Adding a new HTTP-style status code is allowed; consumers MUST treat unknown status codes as the appropriate `2xx`/`4xx`/`5xx` family.

When a new transport lands (e.g., a hypothetical `GrpcBridgeServer`):

1. It MUST decode/encode the same envelope shape and op catalog.
2. It MAY use a non-JSON wire format internally as long as it round-trips the SPI types faithfully.
3. The lifecycle gate (`start()` / `stop()` returns `running == false` → reject new connections) is part of the contract.

The `WebSocketBridgeServer.SERVER_STOPPED` close status (`4503` "bridge-stopped") is a transport-specific detail of the WS implementation; other transports use whatever termination signal their wire format supports.

## Related documents

- `.plans/V1.1-SPI-FOUNDATIONS.md` — F4 spec + op catalog + anti-goals
- `kairo-api/src/main/java/io/kairo/api/bridge/package-info.java` — package-level Javadoc with op catalog table
- `kairo-event-stream-ws/src/main/java/io/kairo/eventstream/ws/KairoBridgeWebSocketHandler.java` — default transport
- `kairo-event-stream-ws/src/test/java/io/kairo/eventstream/ws/KairoBridgeWebSocketHandlerTest.java` — 8 integration tests (5 ops + 3 error paths)
- `docs/roadmap/V1.1-verification.md` — release evidence
- ADR-018 (Unified Event Bus) — server → client side of the conversation
- ADR-023 (SPI Stability Policy) — additive evolution rules + dependency hygiene rationale
- ADR-027 (TenantContext) — `BridgeMeta.tenant` is the propagated `TenantContext`
