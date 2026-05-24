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
package io.kairo.eventstream.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.Experimental;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.BackpressurePolicy;
import io.kairo.api.event.stream.EventStreamFilter;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.api.event.stream.EventStreamSubscriptionRequest;
import io.kairo.eventstream.EventStreamAuthorizationException;
import io.kairo.eventstream.EventStreamService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Transport-layer handler that projects one HTTP SSE request onto a {@link
 * EventStreamSubscription}. The starter wraps this in a {@code @RestController} so application code
 * gets a ready-to-serve endpoint.
 *
 * <p>The handler itself does not depend on any Spring MVC/WebFlux annotation machinery — it is a
 * plain component exposing one reactive method. Tests can drive it directly without a running HTTP
 * server.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("SSE transport — contract may change in v0.10")
public final class KairoEventStreamSseHandler {

    private static final Logger log = LoggerFactory.getLogger(KairoEventStreamSseHandler.class);

    private final EventStreamService service;
    private final ObjectMapper mapper;
    private final int defaultBufferCapacity;
    private final BackpressurePolicy defaultPolicy;

    public KairoEventStreamSseHandler(
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

    /**
     * Open an SSE stream for the given filter and authorization context.
     *
     * @throws EventStreamAuthorizationException when authorization is denied (transport layer maps
     *     to HTTP 403)
     */
    public Flux<ServerSentEvent<String>> stream(
            List<String> domains,
            List<String> eventTypes,
            Integer bufferCapacity,
            BackpressurePolicy policy,
            Map<String, String> authorizationContext) {

        EventStreamFilter filter = buildFilter(domains, eventTypes);
        int capacity = bufferCapacity != null ? bufferCapacity : defaultBufferCapacity;
        BackpressurePolicy effectivePolicy = policy != null ? policy : defaultPolicy;

        EventStreamSubscriptionRequest req =
                new EventStreamSubscriptionRequest(
                        filter,
                        effectivePolicy,
                        capacity,
                        authorizationContext != null ? authorizationContext : Map.of());

        EventStreamSubscription subscription = service.subscribe(req);
        log.debug("SSE subscription opened id={}", subscription.id());

        return subscription
                .events()
                .map(this::toServerSentEvent)
                .doFinally(
                        sig -> {
                            log.debug(
                                    "SSE subscription closed id={} signal={}",
                                    subscription.id(),
                                    sig);
                            subscription.cancel();
                        });
    }

    private ServerSentEvent<String> toServerSentEvent(KairoEvent event) {
        String json;
        try {
            json =
                    mapper.writeValueAsString(
                            Map.of(
                                    "id", event.eventId(),
                                    "domain", event.domain(),
                                    "eventType", event.eventType(),
                                    "timestamp", event.timestamp().toString(),
                                    "attributes", event.attributes()));
        } catch (JsonProcessingException e) {
            // Fall back to an error payload so the subscriber still gets a frame rather than a
            // silent drop. JSON failure here is effectively impossible given the source types.
            json = "{\"error\":\"serialization-failed\"}";
        }
        return ServerSentEvent.<String>builder()
                .id(event.eventId())
                .event(event.domain() + "." + event.eventType())
                .data(json)
                .build();
    }

    private static EventStreamFilter buildFilter(List<String> domains, List<String> eventTypes) {
        boolean hasDomains = domains != null && !domains.isEmpty();
        boolean hasEventTypes = eventTypes != null && !eventTypes.isEmpty();
        if (!hasDomains && !hasEventTypes) {
            return EventStreamFilter.acceptAll();
        }
        if (hasDomains && hasEventTypes) {
            return EventStreamFilter.byDomain(domains.toArray(new String[0]))
                    .and(EventStreamFilter.byEventType(eventTypes.toArray(new String[0])));
        }
        return hasDomains
                ? EventStreamFilter.byDomain(domains.toArray(new String[0]))
                : EventStreamFilter.byEventType(eventTypes.toArray(new String[0]));
    }
}
