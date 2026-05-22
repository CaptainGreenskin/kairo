/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.gateway;

import io.kairo.api.Experimental;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Top-level orchestration interface above {@link Channel}. A single Gateway instance owns N
 * adapters, fans inbound events into a unified flux, and resolves outbound {@link DeliveryTarget}s
 * back to the right adapter.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>Application registers adapters via the builder.
 *   <li>{@link #start()} connects every adapter sequentially; any failure surfaces in the Mono.
 *   <li>While running, subscribe to {@link #inbound()} for incoming messages and call {@link
 *       #deliver(DeliveryTarget, String, java.util.Map)} for outbound replies.
 *   <li>{@link #stop()} disconnects every adapter; subsequent calls are rejected.
 * </ol>
 *
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway SPI — contract may change in v1.x")
public interface Gateway {

    /** Registered adapters, immutable snapshot. */
    Collection<Channel> adapters();

    /** Look up an adapter by id. */
    Optional<Channel> adapter(String channelId);

    /** Connect every adapter; resolves when ready. */
    Mono<Void> start();

    /** Disconnect every adapter; idempotent. */
    Mono<Void> stop();

    /**
     * Unified hot flux of every inbound event from every adapter. Tagged with the source via {@link
     * ChannelMessage#source()}.
     */
    Flux<ChannelMessage> inbound();

    /**
     * Send {@code content} to {@code target}. Resolves to a list because a single logical target
     * (e.g. {@code "telegram"} with no chat id when the adapter exposes more than one home chat)
     * may fan out; in the common case the list has one entry.
     */
    Mono<List<SendResult>> deliver(
            DeliveryTarget target, String content, java.util.Map<String, Object> metadata);

    /** Convenience: parse + deliver in one call. */
    default Mono<List<SendResult>> deliver(String targetSpec, String content) {
        return deliver(DeliveryTarget.parse(targetSpec, null), content, java.util.Map.of());
    }
}
