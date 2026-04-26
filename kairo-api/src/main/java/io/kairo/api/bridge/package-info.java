/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Bridge Protocol SPI — minimal bidirectional RPC for client → agent runtime calls.
 *
 * <p>v1.1 introduces this SPI so that companion clients (TUI / Web / IDE plugin) can drive the same
 * agent runtime: invoking agent runs, cancelling, listing workspaces, approving risky tools, etc.
 * The existing {@code KairoEventBus} stream is server → client only — the bridge fills the client →
 * server gap without exposing agent internals or duplicating the event channel.
 *
 * <h2>Envelope</h2>
 *
 * <p>Schemaless on purpose:
 *
 * <ul>
 *   <li>{@link io.kairo.api.bridge.BridgeRequest} — {@code op} + {@code payload (Map)} + {@code
 *       meta}
 *   <li>{@link io.kairo.api.bridge.BridgeResponse} — HTTP-style {@code status} + {@code payload
 *       (Map)}
 *   <li>{@link io.kairo.api.bridge.BridgeMeta} — correlation id + {@link
 *       io.kairo.api.tenant.TenantContext} + timestamp + free-form attributes
 * </ul>
 *
 * <p>The protocol freezes the envelope shape, not the {@code op} catalog. Adding a new operation
 * never requires a protocol-version bump; semantics of an existing {@code op} <b>may not</b> change
 * once published.
 *
 * <h2>Required v1.1 ops</h2>
 *
 * <ul>
 *   <li>{@code agent.run} — kick off an agent invocation
 *   <li>{@code agent.cancel} — terminate the active invocation
 *   <li>{@code agent.status} — query current session state
 *   <li>{@code tool.approve} — accept / reject a risky tool execution
 *   <li>{@code workspace.list} — enumerate workspaces visible to the principal
 * </ul>
 *
 * <p>The framework supplies the transport; applications register handlers per operation through a
 * single {@link io.kairo.api.bridge.BridgeRequestHandler} (typically a switch on {@code
 * BridgeRequest#op()} or an op-registry).
 *
 * <h2>Anti-goals (v1.1)</h2>
 *
 * <ul>
 *   <li>No file transfer protocol (deferred to v1.3)
 *   <li>No protocol-version negotiation (envelope is schemaless; no need)
 *   <li>No transport-level authentication (Spring Security upstream owns it)
 *   <li>No server-push of bridge frames (use {@code KairoEventBus} for that)
 * </ul>
 *
 * <h2>Stability</h2>
 *
 * <p>Types in this package are {@link io.kairo.api.Stable} since 1.1.0.
 *
 * @since v1.1
 */
package io.kairo.api.bridge;
