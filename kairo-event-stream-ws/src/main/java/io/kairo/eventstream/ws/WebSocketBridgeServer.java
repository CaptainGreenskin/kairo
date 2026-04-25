/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.eventstream.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.Stable;
import io.kairo.api.bridge.BridgeRequestHandler;
import io.kairo.api.bridge.BridgeServer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link BridgeServer} implementation backed by a Spring WebFlux reactive WebSocket
 * endpoint.
 *
 * <p>This class owns the lifecycle flag observed by {@link KairoBridgeWebSocketHandler} — when the
 * server is stopped, the handler closes new sessions with {@link
 * KairoBridgeWebSocketHandler#SERVER_STOPPED} (4503) instead of subscribing them. The actual URL
 * mount is performed by Spring configuration (typically the boot starter); {@code
 * WebSocketBridgeServer} acts as the configuration holder + lifecycle gate, not as a TCP listener
 * of its own.
 *
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Default WebSocket bridge transport")
public final class WebSocketBridgeServer implements BridgeServer {

    private final KairoBridgeWebSocketHandler handler;
    private final String endpoint;
    private final AtomicBoolean running;

    public WebSocketBridgeServer(
            BridgeRequestHandler dispatcher, ObjectMapper mapper, String endpoint) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(endpoint, "endpoint");
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        this.endpoint = endpoint;
        this.running = new AtomicBoolean(false);
        this.handler = new KairoBridgeWebSocketHandler(dispatcher, mapper, running);
    }

    /** Exposes the handler for Spring WebFlux URL routing. */
    public KairoBridgeWebSocketHandler handler() {
        return handler;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public String endpoint() {
        return endpoint;
    }

    /** Visible for tests / diagnostics. */
    public boolean isRunning() {
        return running.get();
    }
}
