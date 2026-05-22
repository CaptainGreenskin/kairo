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
import java.util.Objects;

/**
 * Origin coordinates for an inbound gateway message. Stable enough that two messages from the same
 * user in the same chat compare equal regardless of which media type or thread they're in, because
 * the session key derivation in {@link io.kairo.api.gateway} routing folds {@code (channelId,
 * chatId, threadId)} into the session id and treats {@code userId} as the actor inside that
 * session.
 *
 * <p>Cross-channel pairing (e.g. the same human reaching the agent from Telegram and WeCom) happens
 * one layer up — pairing maps a {@code userId} on one channel to a logical Kairo user across all
 * channels, but the underlying {@code SessionSource} stays per-channel.
 *
 * @param channelId stable id of the originating channel; matches {@code Channel.id()}
 * @param chatId platform-specific chat / room / DM identifier
 * @param userId platform-specific user id; null when the platform doesn't expose one (e.g.
 *     anonymous webhook)
 * @param threadId thread or topic id within {@code chatId}, null for the main timeline
 * @param chatType {@code "dm"}, {@code "group"}, {@code "channel"}, etc.; adapter-defined, null
 *     when the distinction is irrelevant
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway SPI — contract may change in v1.x")
public record SessionSource(
        String channelId, String chatId, String userId, String threadId, String chatType) {

    public SessionSource {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(chatId, "chatId");
        if (channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        if (chatId.isBlank()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
    }

    public static SessionSource of(String channelId, String chatId, String userId) {
        return new SessionSource(channelId, chatId, userId, null, null);
    }
}
