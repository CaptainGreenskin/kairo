/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.channel.dingtalk;

import io.kairo.api.Experimental;
import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.PlatformCapabilities;
import io.kairo.api.gateway.SendResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Gateway {@link Channel} adapter for DingTalk custom bots.
 *
 * <p>Inbound traffic arrives on the webhook endpoint owned by the starter's {@code
 * DingTalkWebhookController}; the controller dispatches verified messages through {@link
 * #dispatchInbound(ChannelMessage)} which emits onto the multicast {@link #inbound()} flux that the
 * gateway consumes.
 *
 * <p>Outbound replies route through {@link #send(DeliveryTarget, String, String, Map)}, which
 * builds the DingTalk JSON payload via {@link DingTalkMessageMapper#toDingTalkPayload} and POSTs it
 * through {@link DingTalkOutboundClient}.
 *
 * <p>Idempotency: repeated deliveries of the same DingTalk {@code msgId} (the adapter's de-dup key)
 * are dropped silently; the inbound flux sees each id at most once per channel lifetime.
 *
 * @since v1.2 (Experimental, gateway-collapsed)
 */
@Experimental("DingTalkChannel — contract may change in v1.x")
public final class DingTalkChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(DingTalkChannel.class);

    private final String id;
    private final DingTalkOutboundClient outboundClient;
    private final List<String> atMobiles;
    private final DingTalkMessageMapper mapper;

    private final Sinks.Many<ChannelMessage> inbound =
            Sinks.many().multicast().onBackpressureBuffer();
    private final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;

    public DingTalkChannel(
            String id,
            DingTalkOutboundClient outboundClient,
            DingTalkMessageMapper mapper,
            List<String> atMobiles) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.outboundClient = Objects.requireNonNull(outboundClient, "outboundClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.atMobiles = atMobiles == null ? List.of() : List.copyOf(atMobiles);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public PlatformCapabilities capabilities() {
        // DingTalk custom bots accept text + markdown sends only; no edit, no draft, no typing
        // indicator, no media uploads via custom-bot webhook. AI Card surfaces (separate API)
        // would supply edit semantics — kept out of the default custom-bot adapter to avoid
        // implying capabilities the webhook can't honour.
        return PlatformCapabilities.textOnly();
    }

    @Override
    public Mono<Void> connect() {
        return Mono.fromRunnable(
                () -> {
                    running = true;
                    log.debug("DingTalkChannel '{}' connected", id);
                });
    }

    @Override
    public Mono<Void> disconnect() {
        return Mono.fromRunnable(
                () -> {
                    running = false;
                    seenMessageIds.clear();
                    inbound.tryEmitComplete();
                    log.debug("DingTalkChannel '{}' disconnected", id);
                });
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
        if (!running) {
            return Mono.just(
                    SendResult.fail(SendResult.FailureMode.UNAVAILABLE, "channel not connected"));
        }
        @SuppressWarnings("unchecked")
        List<String> mobiles =
                metadata != null && metadata.get("atMobiles") instanceof List<?> list
                        ? list.stream().map(String::valueOf).toList()
                        : atMobiles;
        return outboundClient.send(mapper.toDingTalkPayload(content, mobiles));
    }

    /**
     * Drive a verified inbound message into the multicast {@link #inbound()} flux. Deduplicates by
     * DingTalk {@code msgId} so replayed webhook deliveries are coalesced. Callers (the webhook
     * controller) should only invoke this after signature verification succeeds.
     */
    public void dispatchInbound(ChannelMessage message) {
        Objects.requireNonNull(message, "message");
        if (!running) {
            log.debug("DingTalkChannel '{}' dropped inbound — channel not connected", id);
            return;
        }
        String msgId =
                String.valueOf(
                        message.attributes()
                                .getOrDefault(DingTalkMessageMapper.ATTR_MSG_ID, message.id()));
        if (!seenMessageIds.add(msgId)) {
            log.debug(
                    "DingTalkChannel '{}' dropped duplicate msgId {} (idempotency replay)",
                    id,
                    msgId);
            return;
        }
        var emit = inbound.tryEmitNext(message);
        if (emit.isFailure()) {
            log.warn("DingTalkChannel '{}' dropped inbound msgId={} ({})", id, msgId, emit);
        }
    }

    /**
     * Convenience: parse {@code rawJson} via the mapper and dispatch. Used by the webhook
     * controller and TCK.
     */
    public void dispatchInbound(String rawJson) {
        dispatchInbound(mapper.fromDingTalkPayload(rawJson));
    }

    public boolean isRunning() {
        return running;
    }

    /** Visible for testing. */
    DingTalkMessageMapper mapper() {
        return mapper;
    }

    /** Backstop for leak tests; 0 when disconnected. */
    public int dedupSetSize() {
        return seenMessageIds.size();
    }

    /** Convenience factory for tests. */
    public static Duration defaultTimeout() {
        return Duration.ofSeconds(10);
    }
}
