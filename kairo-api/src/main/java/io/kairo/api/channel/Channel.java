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
package io.kairo.api.channel;

import io.kairo.api.Experimental;
import reactor.core.publisher.Mono;

/**
 * Bidirectional integration between Kairo and an external system (IM, webhook, REST, queue, etc.).
 * Implementations own the transport; the application owns the inbound handler ({@link
 * ChannelInboundHandler}).
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>The runtime calls {@link #start(ChannelInboundHandler)} once when the application context
 *       is ready; the channel begins accepting inbound traffic and forwarding to the handler.
 *   <li>{@link #sender()} becomes valid only after {@code start} completes successfully.
 *   <li>{@link #stop()} drains in-flight work and releases transport resources; subsequent calls to
 *       {@link #sender()} return a closed sender that rejects new sends.
 * </ol>
 *
 * <p>Implementations MUST be idempotent w.r.t. {@code start/stop} — calling {@code start} twice
 * without an intervening {@code stop} MAY throw {@link IllegalStateException}, but must never leak
 * resources.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("Channel SPI — contract may change in v0.10")
public interface Channel {

    /**
     * Stable id of this channel (e.g. {@code "slack-prod"}). Used by {@link
     * ChannelIdentity#channelId()} and by applications to look up the channel in a registry.
     */
    String id();

    /** Start accepting inbound traffic and routing it to {@code handler}. */
    Mono<Void> start(ChannelInboundHandler handler);

    /** Drain in-flight work and release transport resources. */
    Mono<Void> stop();

    /**
     * Outbound sender for this channel. Behavior before {@link #start} or after {@link #stop} is
     * implementation-defined but MUST NOT leak resources.
     */
    ChannelOutboundSender sender();
}
