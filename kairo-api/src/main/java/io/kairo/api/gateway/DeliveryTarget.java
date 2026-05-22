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
 * Where a gateway-mediated message should be delivered. Lets callers (cron jobs, send-message
 * tools, agent outputs) name a destination as a single string and have the gateway resolve it,
 * instead of every caller carrying its own channel lookup.
 *
 * <p>Syntax accepted by {@link #parse(String, SessionSource)}:
 *
 * <table>
 *   <tr><th>Form</th><th>Meaning</th></tr>
 *   <tr><td>{@code origin}</td><td>send back to the source of the triggering message</td></tr>
 *   <tr><td>{@code local}</td><td>persist to local mirror only; nothing leaves the host</td></tr>
 *   <tr><td>{@code <channelId>}</td><td>send to the channel's configured home chat</td></tr>
 *   <tr><td>{@code <channelId>:<chatId>}</td><td>send to a specific chat</td></tr>
 *   <tr><td>{@code <channelId>:<chatId>:<threadId>}</td><td>specific chat + thread/topic</td></tr>
 * </table>
 *
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway SPI — contract may change in v1.x")
public record DeliveryTarget(
        String channelId, String chatId, String threadId, boolean isOrigin, boolean isLocal) {

    /** Sentinel channel id used by {@link #isLocal()} targets. */
    public static final String LOCAL_CHANNEL = "local";

    public DeliveryTarget {
        Objects.requireNonNull(channelId, "channelId");
        if (channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
    }

    /** Convenience: {@code channelId} only, default chat / thread. */
    public static DeliveryTarget channel(String channelId) {
        return new DeliveryTarget(channelId, null, null, false, false);
    }

    /** Convenience: explicit chat. */
    public static DeliveryTarget chat(String channelId, String chatId) {
        return new DeliveryTarget(channelId, chatId, null, false, false);
    }

    /** Local-only sink (mirror store). */
    public static DeliveryTarget local() {
        return new DeliveryTarget(LOCAL_CHANNEL, null, null, false, true);
    }

    /** Send back to the originator of an inbound message. */
    public static DeliveryTarget origin(SessionSource source) {
        Objects.requireNonNull(source, "source");
        return new DeliveryTarget(
                source.channelId(), source.chatId(), source.threadId(), true, false);
    }

    /**
     * Parse a string like {@code "telegram:12345"} into a target. {@code "origin"} requires a
     * non-null {@code origin}; unknown strings fall back to {@link #local()}.
     */
    public static DeliveryTarget parse(String spec, SessionSource origin) {
        if (spec == null) return local();
        String s = spec.trim();
        if (s.isEmpty()) return local();
        if ("local".equalsIgnoreCase(s)) return local();
        if ("origin".equalsIgnoreCase(s)) {
            return origin == null ? local() : origin(origin);
        }
        int firstColon = s.indexOf(':');
        if (firstColon < 0) {
            return channel(s.toLowerCase(java.util.Locale.ROOT));
        }
        String channelId = s.substring(0, firstColon).toLowerCase(java.util.Locale.ROOT);
        String rest = s.substring(firstColon + 1);
        int secondColon = rest.indexOf(':');
        if (secondColon < 0) {
            return chat(channelId, rest);
        }
        String chatId = rest.substring(0, secondColon);
        String threadId = rest.substring(secondColon + 1);
        return new DeliveryTarget(channelId, chatId, threadId, false, false);
    }

    @Override
    public String toString() {
        if (isLocal) return "local";
        if (isOrigin) return "origin";
        if (chatId == null) return channelId;
        if (threadId == null) return channelId + ":" + chatId;
        return channelId + ":" + chatId + ":" + threadId;
    }
}
