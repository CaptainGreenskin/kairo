/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.routing;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import io.kairo.gateway.mirror.MirrorStore;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Resolves a {@link DeliveryTarget} to one or more adapter calls, optionally taps the mirror, and
 * aggregates results. Out-of-band so the gateway can stay a thin lifecycle owner.
 *
 * <p>Routing rules:
 *
 * <ul>
 *   <li>{@code local} target — never touches an adapter; the mirror records it under channel {@code
 *       "local"} and we return a single OK result.
 *   <li>{@code origin} / channel-only / channel:chat / channel:chat:thread — look up adapter by
 *       channel id and call {@link Channel#send}.
 *   <li>Unknown channel id — return a single PERMANENT failure with a descriptive message (callers
 *       can fan-out across known channels instead of guessing).
 * </ul>
 *
 * <p>Result is a list because future work (broadcast targets like {@code "all-channels"}) will fan
 * out; today the list has exactly one entry.
 */
public final class DeliveryRouter {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRouter.class);

    private final Map<String, Channel> adapters;
    private final MirrorStore mirror;

    public DeliveryRouter(Map<String, Channel> adapters, MirrorStore mirror) {
        this.adapters = adapters;
        this.mirror = mirror == null ? MirrorStore.noop() : mirror;
    }

    public Mono<List<SendResult>> route(
            DeliveryTarget target, String content, Map<String, Object> metadata) {
        if (target == null) {
            return Mono.just(
                    List.of(SendResult.fail(SendResult.FailureMode.PERMANENT, "null target")));
        }
        if (target.isLocal()) {
            mirror.recordOutboundLocal(content);
            return Mono.just(List.of(SendResult.ok("local-" + System.currentTimeMillis())));
        }
        Channel adapter = adapters.get(target.channelId());
        if (adapter == null) {
            String msg =
                    "No adapter registered for channelId='"
                            + target.channelId()
                            + "' (known: "
                            + adapters.keySet()
                            + ")";
            log.warn(msg);
            return Mono.just(List.of(SendResult.fail(SendResult.FailureMode.PERMANENT, msg)));
        }
        Map<String, Object> meta = metadata == null ? Map.of() : metadata;
        String replyTo = (String) meta.get("replyToMessageId");
        return adapter.send(target, content, replyTo, meta)
                .doOnNext(result -> mirror.recordOutbound(target, content, result))
                .map(List::of)
                .onErrorResume(
                        e ->
                                Mono.just(
                                        List.of(
                                                SendResult.fail(
                                                        SendResult.FailureMode.TRANSIENT,
                                                        e.getMessage() == null
                                                                ? e.getClass().getSimpleName()
                                                                : e.getMessage()))));
    }
}
