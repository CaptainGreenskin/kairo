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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.Stable;
import io.kairo.api.bridge.BridgeMeta;
import io.kairo.api.bridge.BridgeRequest;
import io.kairo.api.bridge.BridgeRequestHandler;
import io.kairo.api.bridge.BridgeResponse;
import io.kairo.api.tenant.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive WebSocket handler that decodes {@link BridgeRequest} envelopes from inbound frames,
 * dispatches them to a {@link BridgeRequestHandler}, and writes correlated {@link BridgeResponse}
 * frames back to the session.
 *
 * <p>This handler is the v1.1 default transport for the bridge protocol. It rides on top of the
 * existing reactive WebSocket stack — no new TCP port is opened, but it is mounted at its own URL
 * pattern (typically {@code /ws/bridge}) so the event-stream and bridge channels do not multiplex
 * onto the same session.
 *
 * <h2>Wire format</h2>
 *
 * <p>Inbound frame ({@code text}, UTF-8 JSON):
 *
 * <pre>
 * { "requestId": "...", "op": "agent.run", "payload": { ... }, "meta": { ... } }
 * </pre>
 *
 * <p>Outbound frame:
 *
 * <pre>
 * { "requestId": "...", "status": 200, "payload": { ... } }
 * </pre>
 *
 * <p>{@code requestId} is supplied by the client; if absent, the server synthesizes a UUID and uses
 * it on the response so the client can still match. Malformed envelopes resolve to a 400 response,
 * unknown ops to 404, handler exceptions to 500. The session itself is never closed on
 * application-level errors — only transport-level failures and explicit shutdown drop the
 * connection.
 *
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Default WebSocket transport for the bridge SPI")
public final class KairoBridgeWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KairoBridgeWebSocketHandler.class);

    private static final TypeReference<Map<String, Object>> MAP_OF_OBJECT =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAP_OF_STRING =
            new TypeReference<>() {};

    /**
     * Close status used when the server has been stopped via {@link WebSocketBridgeServer#stop()}.
     */
    public static final CloseStatus SERVER_STOPPED = new CloseStatus(4503, "bridge-stopped");

    private final BridgeRequestHandler dispatcher;
    private final ObjectMapper mapper;
    private final AtomicBoolean running;

    public KairoBridgeWebSocketHandler(BridgeRequestHandler dispatcher, ObjectMapper mapper) {
        this(dispatcher, mapper, new AtomicBoolean(true));
    }

    KairoBridgeWebSocketHandler(
            BridgeRequestHandler dispatcher, ObjectMapper mapper, AtomicBoolean running) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.running = Objects.requireNonNull(running, "running");
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        if (!running.get()) {
            return session.close(SERVER_STOPPED);
        }
        TenantContext sessionTenant = TenantContext.SINGLE; // auth/tenant resolution is upstream

        Flux<WebSocketMessage> outbound =
                session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .flatMap(text -> handleFrame(text, sessionTenant))
                        .map(envelope -> session.textMessage(serialize(envelope)));

        return session.send(outbound);
    }

    private Mono<Map<String, Object>> handleFrame(String text, TenantContext tenant) {
        Map<String, Object> raw;
        try {
            raw = mapper.readValue(text, MAP_OF_OBJECT);
        } catch (Exception e) {
            log.debug("bridge: malformed envelope ({} chars)", text == null ? 0 : text.length());
            return Mono.just(
                    envelope(
                            synthesizeRequestId(),
                            BridgeResponse.badRequest("malformed-envelope")));
        }
        if (raw == null) {
            return Mono.just(
                    envelope(synthesizeRequestId(), BridgeResponse.badRequest("empty-envelope")));
        }
        Object opObj = raw.get("op");
        String requestId = stringOrSynthesized(raw.get("requestId"));

        if (!(opObj instanceof String op) || op.isBlank()) {
            return Mono.just(envelope(requestId, BridgeResponse.badRequest("missing-op")));
        }
        Map<String, Object> payload = asObjectMap(raw.get("payload"));
        Map<String, String> attrs = asStringMap(raw.get("meta"));
        BridgeMeta meta = new BridgeMeta(requestId, tenant, Instant.now(), attrs);

        BridgeRequest request;
        try {
            request = new BridgeRequest(op, payload, meta);
        } catch (RuntimeException e) {
            return Mono.just(envelope(requestId, BridgeResponse.badRequest("invalid-request")));
        }

        Mono<BridgeResponse> response;
        try {
            response = dispatcher.handle(request);
            if (response == null) {
                response = Mono.just(BridgeResponse.internalError("handler-returned-null"));
            }
        } catch (RuntimeException e) {
            log.debug("bridge handler threw for op={}", op, e);
            response = Mono.just(BridgeResponse.internalError(redact(e.getMessage())));
        }

        return response.onErrorResume(
                        err -> {
                            log.debug("bridge handler signal error for op={}", op, err);
                            return Mono.just(
                                    BridgeResponse.internalError(redact(err.getMessage())));
                        })
                .map(resp -> envelope(requestId, resp));
    }

    private Map<String, Object> asObjectMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> m) {
            return mapper.convertValue(m, MAP_OF_OBJECT);
        }
        return Map.of();
    }

    private Map<String, String> asStringMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> m) {
            return mapper.convertValue(m, MAP_OF_STRING);
        }
        return Map.of();
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.warn("bridge: failed to serialize response envelope", e);
            return "{\"status\":500,\"payload\":{\"error\":\"serialization-failed\"}}";
        }
    }

    private static Map<String, Object> envelope(String requestId, BridgeResponse response) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requestId", requestId);
        out.put("status", response.status());
        out.put("payload", response.payload());
        return out;
    }

    private static String stringOrSynthesized(Object value) {
        if (value instanceof String s && !s.isBlank()) return s;
        return synthesizeRequestId();
    }

    private static String synthesizeRequestId() {
        return UUID.randomUUID().toString();
    }

    private static String redact(String msg) {
        if (msg == null || msg.isBlank()) return "handler-error";
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }
}
