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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Inbound message envelope as the gateway sees it. Carries everything the agent needs to dispatch:
 *
 * <ul>
 *   <li>{@link #type} — what the platform reported (text, voice, image, …)
 *   <li>{@link #attachments} — already cached media files the agent / tools can read directly
 *   <li>{@link #replyToMessageId} / {@link #replyToText} — quoted-message context
 *   <li>{@link #source} — full origin coordinates (channel/chat/user/thread)
 *   <li>{@link #messageId} — platform message id (different from {@link #id}, which is the
 *       gateway's internal trace id)
 * </ul>
 *
 * <p>Text-only channels collapse to {@link MessageType#TEXT} with an empty attachments list — same
 * type, no inheritance hierarchy needed.
 *
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway Channel SPI — contract may change in v1.x")
public record ChannelMessage(
        String id,
        SessionSource source,
        MessageType type,
        String text,
        List<Attachment> attachments,
        String messageId,
        String replyToMessageId,
        String replyToText,
        Instant timestamp,
        Map<String, Object> attributes) {

    public ChannelMessage {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(type, "type");
        text = text == null ? "" : text;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        Objects.requireNonNull(timestamp, "timestamp");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Minimal text message, common case for chat-based IM. */
    public static ChannelMessage text(SessionSource source, String text) {
        return new ChannelMessage(
                UUID.randomUUID().toString(),
                source,
                MessageType.TEXT,
                text,
                List.of(),
                null,
                null,
                null,
                Instant.now(),
                Map.of());
    }

    /** True when the text begins with {@code /}. Detects slash commands without parsing them. */
    public boolean isCommand() {
        return text != null && text.startsWith("/");
    }
}
