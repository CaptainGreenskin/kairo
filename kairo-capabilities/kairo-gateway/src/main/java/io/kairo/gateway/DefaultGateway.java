/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.Gateway;
import io.kairo.api.gateway.SendResult;
import io.kairo.gateway.mirror.MirrorStore;
import io.kairo.gateway.routing.DeliveryRouter;
import io.kairo.gateway.session.SessionDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Default {@link Gateway} implementation. Holds a registry of adapters keyed by id, merges every
 * adapter's inbound flux into a single multicast hot stream, and dispatches outbound deliveries
 * through {@link DeliveryRouter}. {@link MirrorStore} taps every inbound + outbound message for
 * later replay; {@link SessionDirectory} caches the (channel,chat) → session-id mapping so
 * downstream agent runners can join consecutive turns from the same source.
 *
 * <p>Lifecycle is strict: {@link #start()} fails if called twice without {@link #stop()}; {@link
 * #deliver} rejects before start and after stop. Adapter connect/disconnect is sequential — a
 * single failed adapter aborts {@link #start()} so the application sees a clean failure instead of
 * a half-connected gateway.
 */
public final class DefaultGateway implements Gateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultGateway.class);

    private enum State {
        NEW,
        STARTED,
        STOPPED
    }

    private final Map<String, Channel> adapters;
    private final DeliveryRouter router;
    private final SessionDirectory sessions;
    private final MirrorStore mirror;
    private final Sinks.Many<ChannelMessage> inbound;
    private final List<Disposable> subscriptions =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public DefaultGateway(
            Collection<Channel> adapters, SessionDirectory sessions, MirrorStore mirror) {
        Map<String, Channel> indexed = new ConcurrentHashMap<>();
        for (Channel a : adapters) {
            if (indexed.put(a.id(), a) != null) {
                throw new IllegalArgumentException(
                        "Duplicate adapter id: "
                                + a.id()
                                + " — every adapter must have a unique id");
            }
        }
        this.adapters = indexed;
        this.sessions = sessions == null ? new SessionDirectory() : sessions;
        this.mirror = mirror == null ? MirrorStore.noop() : mirror;
        this.router = new DeliveryRouter(this.adapters, this.mirror);
        this.inbound = Sinks.many().multicast().onBackpressureBuffer(256, false);
    }

    @Override
    public Collection<Channel> adapters() {
        return List.copyOf(adapters.values());
    }

    @Override
    public Optional<Channel> adapter(String channelId) {
        return Optional.ofNullable(adapters.get(channelId));
    }

    @Override
    public Mono<Void> start() {
        if (!state.compareAndSet(State.NEW, State.STARTED)) {
            return Mono.error(
                    new IllegalStateException(
                            "Gateway already started or stopped; current state=" + state.get()));
        }
        if (adapters.isEmpty()) {
            log.warn("Gateway starting with zero adapters — inbound flux will be empty");
            return Mono.empty();
        }
        return Flux.fromIterable(adapters.values()).concatMap(this::connectAndSubscribe).then();
    }

    private Mono<Void> connectAndSubscribe(Channel adapter) {
        return adapter.connect()
                .doOnSuccess(
                        v -> {
                            // Re-publish every adapter's inbound into the merged sink and record
                            // to mirror so historians can replay the exact wire timeline. Sink emit
                            // is fail-fast in DROP_ON_FULL mode; we log + skip rather than block
                            // the producer.
                            Disposable sub =
                                    adapter.inbound()
                                            .doOnNext(msg -> mirror.recordInbound(msg))
                                            .doOnNext(msg -> sessions.note(msg.source()))
                                            .subscribe(
                                                    msg -> {
                                                        var result = inbound.tryEmitNext(msg);
                                                        if (result.isFailure()) {
                                                            log.warn(
                                                                    "Dropped inbound message from {} due to {}",
                                                                    adapter.id(),
                                                                    result);
                                                        }
                                                    },
                                                    err ->
                                                            log.warn(
                                                                    "Adapter '{}' inbound flux errored: {}",
                                                                    adapter.id(),
                                                                    err.getMessage()));
                            subscriptions.add(sub);
                            log.info("Gateway adapter '{}' connected", adapter.id());
                        });
    }

    @Override
    public Mono<Void> stop() {
        if (!state.compareAndSet(State.STARTED, State.STOPPED)) {
            // Idempotent: stopping a stopped or never-started gateway is fine.
            return Mono.empty();
        }
        subscriptions.forEach(Disposable::dispose);
        subscriptions.clear();
        inbound.tryEmitComplete();
        return Flux.fromIterable(adapters.values())
                .concatMap(
                        a ->
                                a.disconnect()
                                        .onErrorResume(
                                                err -> {
                                                    log.warn(
                                                            "Adapter '{}' disconnect failed: {}",
                                                            a.id(),
                                                            err.getMessage());
                                                    return Mono.empty();
                                                }))
                .then();
    }

    @Override
    public Flux<ChannelMessage> inbound() {
        return inbound.asFlux();
    }

    @Override
    public Mono<List<SendResult>> deliver(
            DeliveryTarget target, String content, Map<String, Object> metadata) {
        if (state.get() != State.STARTED) {
            return Mono.error(
                    new IllegalStateException("Gateway not started; current state=" + state.get()));
        }
        return router.route(target, content, metadata);
    }

    /** Test helper: surfaces the registered session directory so consumers can pre-pair. */
    public SessionDirectory sessions() {
        return sessions;
    }

    /** Test helper. */
    public MirrorStore mirror() {
        return mirror;
    }
}
