/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.stream;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Bridges an agent's token-stream into the platform message lifecycle: an initial {@code send},
 * then repeated {@code editMessage} (or {@code sendDraft} when supported) as more tokens arrive,
 * then a final edit on completion.
 *
 * <p>Without this, every channel adapter had to implement its own debounced edit loop, draft
 * fallback, capability detection, and back-pressure handling — exactly the duplication this module
 * exists to eliminate.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Buffer incoming text deltas. Wait until either {@code minIntervalMs} has elapsed since the
 *       last edit OR a sentence boundary is observed.
 *   <li>If no message yet: call {@code adapter.send} with the buffered text.
 *   <li>Otherwise: if {@code supportsDraft}, call {@code sendDraft} with a stable draft id.
 *       Otherwise, call {@code editMessage} with the running text.
 *   <li>On stream complete: do a final edit with the full text (or send if the message never
 *       materialised — e.g. zero tokens received).
 * </ol>
 */
public final class StreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(StreamConsumer.class);

    /** Minimum wall-clock gap between successive edits. Tunable per consumer. */
    private final Duration minInterval;

    private final long draftId;

    public StreamConsumer() {
        this(Duration.ofMillis(800));
    }

    public StreamConsumer(Duration minInterval) {
        this.minInterval = minInterval;
        this.draftId = System.nanoTime();
    }

    /**
     * Consume {@code tokens} and emit a single {@link SendResult} when the stream completes (or
     * fails). Empty token stream still produces a result — adapters that prefer to suppress empty
     * sends can short-circuit upstream.
     */
    public Mono<SendResult> consume(Channel adapter, DeliveryTarget target, Flux<String> tokens) {
        var caps = adapter.capabilities();
        StringBuilder accumulator = new StringBuilder();
        AtomicReference<String> messageId = new AtomicReference<>();
        AtomicReference<Long> lastEditAt = new AtomicReference<>(0L);

        return tokens.filter(t -> t != null && !t.isEmpty())
                .concatMap(
                        delta -> {
                            accumulator.append(delta);
                            long now = System.currentTimeMillis();
                            // Throttle intermediate updates so we don't hammer platform rate
                            // limits. Sentence-end punctuation flushes immediately so the user
                            // sees natural breakpoints right away.
                            boolean punctuated = delta.matches(".*[.!?。！？\\n].*");
                            if (!punctuated && now - lastEditAt.get() < minInterval.toMillis()) {
                                return Mono.empty();
                            }
                            lastEditAt.set(now);
                            return pushUpdate(
                                    adapter, target, caps, accumulator.toString(), messageId);
                        })
                .then(
                        Mono.defer(
                                () -> {
                                    String finalText = accumulator.toString();
                                    if (messageId.get() == null) {
                                        // Edge case: stream ended without a single emission — send
                                        // a one-shot so the user still gets a reply (or an empty
                                        // bubble we can later overwrite if more tokens arrive).
                                        return adapter.send(
                                                        target, finalText, null, java.util.Map.of())
                                                .doOnNext(r -> messageId.set(r.messageId()));
                                    }
                                    if (caps.supportsEdit()) {
                                        return adapter.editMessage(
                                                target, messageId.get(), finalText);
                                    }
                                    return Mono.just(SendResult.ok(messageId.get()));
                                }));
    }

    private Mono<SendResult> pushUpdate(
            Channel adapter,
            DeliveryTarget target,
            io.kairo.api.gateway.PlatformCapabilities caps,
            String text,
            AtomicReference<String> messageId) {
        if (messageId.get() == null) {
            return adapter.send(target, text, null, java.util.Map.of())
                    .doOnNext(r -> messageId.set(r.messageId()));
        }
        if (caps.supportsDraft()) {
            return adapter.sendDraft(target, draftId, text)
                    .onErrorResume(
                            err -> {
                                log.debug(
                                        "sendDraft failed on {}, falling back to editMessage: {}",
                                        adapter.id(),
                                        err.getMessage());
                                return adapter.editMessage(target, messageId.get(), text);
                            });
        }
        if (caps.supportsEdit()) {
            return adapter.editMessage(target, messageId.get(), text);
        }
        // No edit support and we already sent the initial message — the caller will get a single
        // bubble that won't visibly stream. Best we can do without the platform's cooperation.
        return Mono.just(SendResult.ok(messageId.get()));
    }
}
