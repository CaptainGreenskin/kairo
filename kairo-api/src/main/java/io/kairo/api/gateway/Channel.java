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
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A single channel into the {@link Gateway}: an IM platform, webhook source, REST poller, or
 * anywhere else messages enter / leave the agent. One channel implementation per platform.
 *
 * <p>The contract is intentionally small. Only {@link #id()}, {@link #capabilities()}, {@link
 * #connect()}, {@link #disconnect()}, {@link #inbound()}, and {@link #send(DeliveryTarget, String,
 * String, Map)} are mandatory; the rest of the surface (media uploads, edit-message streaming,
 * typing indicators, draft animation) has sensible defaults that return {@link SendResult#fail
 * UNAVAILABLE}. The gateway branches on {@link #capabilities()} to avoid calling unsupported
 * methods, so simple text-only adapters need only the six mandatory methods.
 *
 * <p>All operations are reactive — implementations MUST NOT block the calling thread.
 *
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway Channel SPI — contract may change in v1.x")
public interface Channel {

    /** Stable id used by {@link DeliveryTarget#channelId()}, e.g. {@code "telegram"}. */
    String id();

    /** Declared optional features — gateway branches on this to avoid trial-and-error. */
    PlatformCapabilities capabilities();

    /** Start the channel. Resolves when ready to receive on {@link #inbound()}. */
    Mono<Void> connect();

    /** Drain in-flight work and release resources. Idempotent. */
    Mono<Void> disconnect();

    /**
     * Hot flux of inbound events. The gateway subscribes once after {@link #connect()} succeeds;
     * channels must serve every subscriber the same events (use a {@code Sinks.Many.multicast} or
     * equivalent).
     */
    Flux<ChannelMessage> inbound();

    /**
     * One-shot text send to the resolved target. {@code replyToMessageId} may be null. {@code
     * metadata} carries platform-specific knobs (Telegram parse_mode, DingTalk card markup, etc.).
     */
    Mono<SendResult> send(
            DeliveryTarget target,
            String content,
            String replyToMessageId,
            Map<String, Object> metadata);

    /** Send media. Default: refuse. */
    default Mono<SendResult> sendAttachment(
            DeliveryTarget target,
            Attachment attachment,
            String caption,
            Map<String, Object> metadata) {
        return Mono.just(
                SendResult.fail(
                        SendResult.FailureMode.UNAVAILABLE,
                        id() + " does not implement sendAttachment"));
    }

    /** Edit an existing message identified by {@code messageId}. Default: refuse. */
    default Mono<SendResult> editMessage(
            DeliveryTarget target, String messageId, String newContent) {
        return Mono.just(
                SendResult.fail(
                        SendResult.FailureMode.UNAVAILABLE,
                        id() + " does not implement editMessage"));
    }

    /** Delete a previously-sent message. Default: refuse. */
    default Mono<SendResult> deleteMessage(DeliveryTarget target, String messageId) {
        return Mono.just(
                SendResult.fail(
                        SendResult.FailureMode.UNAVAILABLE,
                        id() + " does not implement deleteMessage"));
    }

    /**
     * Push a typing / "agent is thinking" indicator. Best-effort, ignore result. Default: no-op.
     */
    default Mono<Void> sendTyping(DeliveryTarget target) {
        return Mono.empty();
    }

    /**
     * Animated streaming-draft send. Channels that implement this must return true from {@link
     * PlatformCapabilities#supportsDraft()} so the stream consumer takes the native-draft path
     * instead of the edit-message fallback. {@code draftId} is stable across consecutive calls
     * within one response.
     */
    default Mono<SendResult> sendDraft(DeliveryTarget target, long draftId, String content) {
        return Mono.just(
                SendResult.fail(
                        SendResult.FailureMode.UNAVAILABLE,
                        id() + " does not implement sendDraft"));
    }
}
