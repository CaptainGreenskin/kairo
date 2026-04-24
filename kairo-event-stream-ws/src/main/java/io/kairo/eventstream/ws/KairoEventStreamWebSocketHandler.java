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
import io.kairo.api.Experimental;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.BackpressurePolicy;
import io.kairo.api.event.stream.EventStreamFilter;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.api.event.stream.EventStreamSubscriptionRequest;
import io.kairo.eventstream.EventStreamAuthorizationException;
import io.kairo.eventstream.EventStreamService;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive WebSocket handler that pipes a {@link io.kairo.api.event.KairoEventBus} subscription
 * onto one WebSocket session. Deny-safe: if the configured authorizer refuses the handshake, the
 * session is closed with 4403 (policy violation).
 *
 * <p>Filter parameters come from the handshake URI query string; client-supplied headers are
 * forwarded to the authorizer as the authorization context.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("WebSocket transport — contract may change in v0.10")
public final class KairoEventStreamWebSocketHandler implements WebSocketHandler {

    /** WebSocket close status 4403 — application-level forbidden (deny-safe auth). */
    public static final CloseStatus UNAUTHORIZED = new CloseStatus(4403, "unauthorized");

    /** WebSocket close status 4413 — application-level overflow (buffer limit hit). */
    public static final CloseStatus BUFFER_OVERFLOW = new CloseStatus(4413, "buffer-overflow");

    private static final Logger log =
            LoggerFactory.getLogger(KairoEventStreamWebSocketHandler.class);

    private final EventStreamService service;
    private final ObjectMapper mapper;
    private final int defaultBufferCapacity;
    private final BackpressurePolicy defaultPolicy;

    public KairoEventStreamWebSocketHandler(
            EventStreamService service,
            ObjectMapper mapper,
            int defaultBufferCapacity,
            BackpressurePolicy defaultPolicy) {
        this.service = Objects.requireNonNull(service, "service");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        if (defaultBufferCapacity <= 0) {
            throw new IllegalArgumentException("defaultBufferCapacity must be > 0");
        }
        this.defaultBufferCapacity = defaultBufferCapacity;
        this.defaultPolicy = Objects.requireNonNull(defaultPolicy, "defaultPolicy");
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Map<String, List<String>> query = parseQuery(session.getHandshakeInfo().getUri());
        Map<String, String> authContext = extractAuthContext(session);

        EventStreamSubscription subscription;
        try {
            subscription = openSubscription(query, authContext);
        } catch (EventStreamAuthorizationException ex) {
            log.debug("WS subscription denied reason={}", ex.getMessage());
            return session.close(UNAUTHORIZED.withReason(truncate(ex.getMessage(), 60)));
        }

        Flux<WebSocketMessage> outbound =
                subscription.events().map(event -> toFrame(session, event));

        Mono<Void> control = handleControlMessages(session, subscription);

        return session.send(outbound)
                .and(control)
                .doFinally(
                        sig -> {
                            log.debug(
                                    "WS subscription closing id={} signal={}",
                                    subscription.id(),
                                    sig);
                            subscription.cancel();
                        });
    }

    private EventStreamSubscription openSubscription(
            Map<String, List<String>> query, Map<String, String> authContext) {
        EventStreamFilter filter = buildFilter(query);
        int capacity = parseInt(query, "bufferCapacity", defaultBufferCapacity);
        BackpressurePolicy policy = parsePolicy(query, defaultPolicy);
        EventStreamSubscriptionRequest req =
                new EventStreamSubscriptionRequest(filter, policy, capacity, authContext);
        return service.subscribe(req);
    }

    private Mono<Void> handleControlMessages(
            WebSocketSession session, EventStreamSubscription subscription) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> onControlFrame(session, subscription, payload))
                .then();
    }

    private Mono<Void> onControlFrame(
            WebSocketSession session, EventStreamSubscription subscription, String payload) {
        ControlMessage msg;
        try {
            msg = mapper.readValue(payload, ControlMessage.class);
        } catch (Exception e) {
            return Mono.empty();
        }
        if (msg == null || msg.type() == null) {
            return Mono.empty();
        }
        return switch (msg.type()) {
            case "ping" -> {
                WebSocketMessage pong = session.textMessage("{\"type\":\"pong\"}");
                yield session.send(Mono.just(pong));
            }
            case "unsubscribe" -> {
                subscription.cancel();
                yield session.close(CloseStatus.NORMAL);
            }
            default -> Mono.empty();
        };
    }

    private WebSocketMessage toFrame(WebSocketSession session, KairoEvent event) {
        try {
            String json =
                    mapper.writeValueAsString(
                            Map.of(
                                    "id", event.eventId(),
                                    "domain", event.domain(),
                                    "eventType", event.eventType(),
                                    "timestamp", event.timestamp().toString(),
                                    "attributes", event.attributes()));
            return session.textMessage(json);
        } catch (Exception e) {
            return session.textMessage("{\"error\":\"serialization-failed\"}");
        }
    }

    private static EventStreamFilter buildFilter(Map<String, List<String>> query) {
        List<String> domains = query.getOrDefault("domain", List.of());
        List<String> eventTypes = query.getOrDefault("eventType", List.of());
        boolean hasD = !domains.isEmpty();
        boolean hasE = !eventTypes.isEmpty();
        if (!hasD && !hasE) {
            return EventStreamFilter.acceptAll();
        }
        if (hasD && hasE) {
            return EventStreamFilter.byDomain(domains.toArray(new String[0]))
                    .and(EventStreamFilter.byEventType(eventTypes.toArray(new String[0])));
        }
        return hasD
                ? EventStreamFilter.byDomain(domains.toArray(new String[0]))
                : EventStreamFilter.byEventType(eventTypes.toArray(new String[0]));
    }

    private static int parseInt(Map<String, List<String>> query, String key, int fallback) {
        List<String> values = query.get(key);
        if (values == null || values.isEmpty()) return fallback;
        try {
            return Integer.parseInt(values.get(0));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static BackpressurePolicy parsePolicy(
            Map<String, List<String>> query, BackpressurePolicy fallback) {
        List<String> values = query.get("policy");
        if (values == null || values.isEmpty()) return fallback;
        try {
            return BackpressurePolicy.valueOf(values.get(0));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Map<String, String> extractAuthContext(WebSocketSession session) {
        Map<String, String> out = new HashMap<>();
        session.getHandshakeInfo()
                .getHeaders()
                .forEach(
                        (name, values) -> {
                            if (!values.isEmpty()) {
                                out.put(name.toLowerCase(), values.get(0));
                            }
                        });
        return out;
    }

    static Map<String, List<String>> parseQuery(URI uri) {
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        return Arrays.stream(raw.split("&"))
                .map(kv -> kv.split("=", 2))
                .filter(kv -> kv.length == 2 && !kv[0].isEmpty())
                .collect(
                        Collectors.groupingBy(
                                kv ->
                                        java.net.URLDecoder.decode(
                                                kv[0], java.nio.charset.StandardCharsets.UTF_8),
                                Collectors.mapping(
                                        kv ->
                                                java.net.URLDecoder.decode(
                                                        kv[1],
                                                        java.nio.charset.StandardCharsets.UTF_8),
                                        Collectors.toList())));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** JSON-decoded client control frame. Public so Jackson can instantiate it via records. */
    public record ControlMessage(String type) {}
}
