/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.mirror;

import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;

/**
 * Append-only sink for every inbound + outbound gateway exchange. Lets operators replay an incident
 * off-line without re-driving the live transports.
 *
 * <p>Two implementations ship today:
 *
 * <ul>
 *   <li>{@link #noop()} — discards everything (the default when no mirror is configured)
 *   <li>{@link JsonlMirrorStore} — one JSON line per event, fsync'd on rotation
 * </ul>
 */
public interface MirrorStore {

    void recordInbound(ChannelMessage message);

    void recordOutbound(DeliveryTarget target, String content, SendResult result);

    /** Called for {@link DeliveryTarget#local()} targets — they never touch an adapter. */
    void recordOutboundLocal(String content);

    /** No-op implementation. Default when no mirror is wired. */
    static MirrorStore noop() {
        return NoopMirrorStore.INSTANCE;
    }

    /** Internal singleton. */
    final class NoopMirrorStore implements MirrorStore {
        static final NoopMirrorStore INSTANCE = new NoopMirrorStore();

        private NoopMirrorStore() {}

        @Override
        public void recordInbound(ChannelMessage message) {}

        @Override
        public void recordOutbound(DeliveryTarget target, String content, SendResult result) {}

        @Override
        public void recordOutboundLocal(String content) {}
    }
}
