/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.PlatformCapabilities;
import io.kairo.api.gateway.SendResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Test double — captures sends, exposes a sink for injecting inbound events. */
public final class FakeAdapter implements Channel {
    private final String id;
    private final PlatformCapabilities caps;
    private final Sinks.Many<ChannelMessage> inbound =
            Sinks.many().multicast().onBackpressureBuffer();
    public final List<String> sends = new CopyOnWriteArrayList<>();
    public final List<String> edits = new CopyOnWriteArrayList<>();
    public final AtomicInteger drafts = new AtomicInteger();
    public final AtomicInteger sendCount = new AtomicInteger();
    private boolean connected;
    public RuntimeException connectFailure;
    public SendResult.FailureMode forceFailure;

    public FakeAdapter(String id) {
        this(id, PlatformCapabilities.builder().edit().build());
    }

    public FakeAdapter(String id, PlatformCapabilities caps) {
        this.id = id;
        this.caps = caps;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public PlatformCapabilities capabilities() {
        return caps;
    }

    @Override
    public Mono<Void> connect() {
        if (connectFailure != null) return Mono.error(connectFailure);
        connected = true;
        return Mono.empty();
    }

    @Override
    public Mono<Void> disconnect() {
        connected = false;
        inbound.tryEmitComplete();
        return Mono.empty();
    }

    @Override
    public Flux<ChannelMessage> inbound() {
        return inbound.asFlux();
    }

    @Override
    public Mono<SendResult> send(
            DeliveryTarget target,
            String content,
            String replyToMessageId,
            Map<String, Object> metadata) {
        if (forceFailure != null) {
            return Mono.just(SendResult.fail(forceFailure, "forced"));
        }
        sends.add(content);
        return Mono.just(SendResult.ok("msg-" + sendCount.incrementAndGet()));
    }

    @Override
    public Mono<SendResult> editMessage(
            DeliveryTarget target, String messageId, String newContent) {
        edits.add(newContent);
        return Mono.just(SendResult.ok(messageId));
    }

    @Override
    public Mono<SendResult> sendDraft(DeliveryTarget target, long draftId, String content) {
        drafts.incrementAndGet();
        return Mono.just(SendResult.ok("draft-" + draftId));
    }

    public void emit(ChannelMessage msg) {
        inbound.tryEmitNext(msg);
    }

    public boolean connected() {
        return connected;
    }
}
