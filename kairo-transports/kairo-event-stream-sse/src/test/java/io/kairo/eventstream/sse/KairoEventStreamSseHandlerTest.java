/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.eventstream.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.BackpressurePolicy;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.api.event.stream.EventStreamSubscriptionRequest;
import io.kairo.eventstream.EventStreamAuthorizationException;
import io.kairo.eventstream.EventStreamService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class KairoEventStreamSseHandlerTest {

    @Test
    void propagatesAuthorizationDenialAsException() {
        EventStreamService service =
                new EventStreamService() {
                    @Override
                    public EventStreamSubscription subscribe(EventStreamSubscriptionRequest req) {
                        throw new EventStreamAuthorizationException("denied");
                    }

                    @Override
                    public int activeSubscriptionCount() {
                        return 0;
                    }
                };
        KairoEventStreamSseHandler handler =
                new KairoEventStreamSseHandler(
                        service, new ObjectMapper(), 32, BackpressurePolicy.BUFFER_DROP_OLDEST);

        EventStreamAuthorizationException ex =
                assertThrows(
                        EventStreamAuthorizationException.class,
                        () -> handler.stream(List.of(), List.of(), null, null, Map.of()));
        assertEquals("denied", ex.getMessage());
    }

    @Test
    void emitsSseFramesForMatchingEvents() throws Exception {
        Sinks.Many<KairoEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        AtomicBoolean cancelled = new AtomicBoolean();
        EventStreamSubscription sub =
                new EventStreamSubscription() {
                    @Override
                    public String id() {
                        return "sub-1";
                    }

                    @Override
                    public Flux<KairoEvent> events() {
                        return sink.asFlux();
                    }

                    @Override
                    public void cancel() {
                        cancelled.set(true);
                    }

                    @Override
                    public boolean isActive() {
                        return !cancelled.get();
                    }
                };
        EventStreamService service = stubService(sub);

        KairoEventStreamSseHandler handler =
                new KairoEventStreamSseHandler(
                        service, new ObjectMapper(), 32, BackpressurePolicy.BUFFER_DROP_OLDEST);

        CopyOnWriteArrayList<ServerSentEvent<String>> received = new CopyOnWriteArrayList<>();
        Disposable d =
                handler.stream(List.of("execution"), null, null, null, Map.of())
                        .subscribe(received::add);

        KairoEvent e = KairoEvent.of("execution", "MODEL_CALL", Map.of("k", "v"));
        sink.tryEmitNext(e);

        waitUntil(() -> received.size() == 1);
        assertEquals(1, received.size());
        ServerSentEvent<String> frame = received.get(0);
        assertEquals(e.eventId(), frame.id());
        assertEquals("execution.MODEL_CALL", frame.event());
        assertNotNull(frame.data());
        assertTrue(frame.data().contains("\"domain\":\"execution\""));
        assertTrue(frame.data().contains("\"eventType\":\"MODEL_CALL\""));

        d.dispose();
        assertTrue(cancelled.get());
    }

    @Test
    void usesDefaultCapacityAndPolicyWhenNotProvided() {
        CopyOnWriteArrayList<EventStreamSubscriptionRequest> captured =
                new CopyOnWriteArrayList<>();
        EventStreamService service =
                new EventStreamService() {
                    @Override
                    public EventStreamSubscription subscribe(EventStreamSubscriptionRequest req) {
                        captured.add(req);
                        return noopSubscription();
                    }

                    @Override
                    public int activeSubscriptionCount() {
                        return 0;
                    }
                };

        KairoEventStreamSseHandler handler =
                new KairoEventStreamSseHandler(
                        service, new ObjectMapper(), 64, BackpressurePolicy.ERROR_ON_OVERFLOW);
        handler.stream(null, null, null, null, null).subscribe().dispose();

        assertEquals(1, captured.size());
        EventStreamSubscriptionRequest req = captured.get(0);
        assertEquals(64, req.bufferCapacity());
        assertEquals(BackpressurePolicy.ERROR_ON_OVERFLOW, req.backpressurePolicy());
        assertEquals(Map.of(), req.authorizationContext());
    }

    private static EventStreamService stubService(EventStreamSubscription sub) {
        return new EventStreamService() {
            @Override
            public EventStreamSubscription subscribe(EventStreamSubscriptionRequest req) {
                return sub;
            }

            @Override
            public int activeSubscriptionCount() {
                return 1;
            }
        };
    }

    private static EventStreamSubscription noopSubscription() {
        return new EventStreamSubscription() {
            @Override
            public String id() {
                return "noop";
            }

            @Override
            public Flux<KairoEvent> events() {
                return Flux.empty();
            }

            @Override
            public void cancel() {}

            @Override
            public boolean isActive() {
                return false;
            }
        };
    }

    private static void waitUntil(java.util.function.BooleanSupplier p)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (p.getAsBoolean()) return;
            Thread.sleep(10L);
        }
    }
}
