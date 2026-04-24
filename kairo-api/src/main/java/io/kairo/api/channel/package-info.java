/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Channel SPI — contract for bidirectional integration between external systems (IM, webhook, REST,
 * ticket queues, etc.) and Kairo Agents / Teams.
 *
 * <p>Design goals (ADR-017):
 *
 * <ul>
 *   <li><b>Inbound</b> — {@link io.kairo.api.channel.ChannelInboundHandler} adapts an external
 *       message into a {@link io.kairo.api.channel.ChannelMessage} that Kairo agents can consume.
 *   <li><b>Outbound</b> — {@link io.kairo.api.channel.ChannelOutboundSender} pushes agent replies
 *       back to the external system.
 *   <li><b>Lifecycle</b> — {@link io.kairo.api.channel.Channel} wraps both directions and exposes
 *       {@code start()} / {@code stop()} so the runtime can boot / drain adapters cleanly.
 *   <li><b>Identity</b> — every inbound message carries a {@link
 *       io.kairo.api.channel.ChannelIdentity} the application can use for authorization and
 *       routing; the SPI does NOT assume a user/session model itself.
 * </ul>
 *
 * <p>Shipping in v0.9 GA: SPI surface + a reference {@code LoopbackChannel} for tests / demos. Real
 * IM adapters (DingTalk / 飞书 / Slack / webhook) ship as separate modules in the v0.9.x train, each
 * consuming the frozen SPI.
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.api.channel;
