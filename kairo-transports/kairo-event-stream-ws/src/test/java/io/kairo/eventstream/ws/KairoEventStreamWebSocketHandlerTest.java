/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.eventstream.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.BackpressurePolicy;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.api.event.stream.EventStreamSubscriptionRequest;
import io.kairo.eventstream.EventStreamAuthorizationException;
import io.kairo.eventstream.EventStreamService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class KairoEventStreamWebSocketHandlerTest {

    @Test
    void parseQueryHandlesEmptyString() {
        assertEquals(Map.of(), KairoEventStreamWebSocketHandler.parseQuery(URI.create("ws://x/y")));
    }

    @Test
    void parseQueryGroupsRepeatedKeys() {
        Map<String, List<String>> parsed =
                KairoEventStreamWebSocketHandler.parseQuery(
                        URI.create("ws://x/y?domain=execution&domain=team&bufferCapacity=32"));
        assertEquals(List.of("execution", "team"), parsed.get("domain"));
        assertEquals(List.of("32"), parsed.get("bufferCapacity"));
    }

    @Test
    void parseQueryDecodesPercentEncoding() {
        Map<String, List<String>> parsed =
                KairoEventStreamWebSocketHandler.parseQuery(URI.create("ws://x/y?eventType=A%20B"));
        assertEquals(List.of("A B"), parsed.get("eventType"));
    }

    @Test
    void handleClosesWith4403WhenAuthorizationDenied() {
        EventStreamService service =
                new EventStreamService() {
                    @Override
                    public EventStreamSubscription subscribe(EventStreamSubscriptionRequest r) {
                        throw new EventStreamAuthorizationException("nope");
                    }

                    @Override
                    public int activeSubscriptionCount() {
                        return 0;
                    }
                };
        KairoEventStreamWebSocketHandler h =
                new KairoEventStreamWebSocketHandler(
                        service, new ObjectMapper(), 16, BackpressurePolicy.BUFFER_DROP_OLDEST);

        HandshakeInfo hs =
                new HandshakeInfo(
                        URI.create("ws://host/stream"), new HttpHeaders(), Mono.empty(), null);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeInfo()).thenReturn(hs);
        when(session.close(any(CloseStatus.class))).thenReturn(Mono.empty());

        h.handle(session).block();

        ArgumentCaptor<CloseStatus> cs = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(cs.capture());
        assertEquals(4403, cs.getValue().getCode());
        assertNotNull(cs.getValue().getReason());
        assertTrue(cs.getValue().getReason().contains("nope"));
    }

    @Test
    void handleStreamsEventsWhenAuthorized() {
        KairoEvent evt = KairoEvent.of("execution", "MODEL_CALL", Map.of());
        EventStreamSubscription sub =
                new EventStreamSubscription() {
                    private volatile boolean active = true;

                    @Override
                    public String id() {
                        return "s1";
                    }

                    @Override
                    public Flux<KairoEvent> events() {
                        return Flux.just(evt);
                    }

                    @Override
                    public void cancel() {
                        active = false;
                    }

                    @Override
                    public boolean isActive() {
                        return active;
                    }
                };
        CopyOnWriteArrayList<EventStreamSubscriptionRequest> capturedReq =
                new CopyOnWriteArrayList<>();
        EventStreamService service =
                new EventStreamService() {
                    @Override
                    public EventStreamSubscription subscribe(EventStreamSubscriptionRequest r) {
                        capturedReq.add(r);
                        return sub;
                    }

                    @Override
                    public int activeSubscriptionCount() {
                        return 1;
                    }
                };

        KairoEventStreamWebSocketHandler h =
                new KairoEventStreamWebSocketHandler(
                        service, new ObjectMapper(), 16, BackpressurePolicy.BUFFER_DROP_OLDEST);

        HandshakeInfo hs =
                new HandshakeInfo(
                        URI.create("ws://host/stream?domain=execution"),
                        new HttpHeaders(),
                        Mono.empty(),
                        null);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeInfo()).thenReturn(hs);
        when(session.receive()).thenReturn(Flux.empty());

        DefaultDataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
        when(session.textMessage(anyString()))
                .thenAnswer(
                        inv -> {
                            String text = inv.getArgument(0);
                            return new WebSocketMessage(
                                    WebSocketMessage.Type.TEXT,
                                    bufferFactory.wrap(text.getBytes(StandardCharsets.UTF_8)));
                        });

        CopyOnWriteArrayList<WebSocketMessage> sent = new CopyOnWriteArrayList<>();
        when(session.send(any()))
                .thenAnswer(
                        inv -> {
                            org.reactivestreams.Publisher<WebSocketMessage> p = inv.getArgument(0);
                            return Flux.from(p).doOnNext(sent::add).then();
                        });

        h.handle(session).block();

        assertEquals(1, capturedReq.size());
        assertEquals(16, capturedReq.get(0).bufferCapacity());
        assertEquals(1, sent.size());
        verify(session).textMessage(anyString());
    }
}
