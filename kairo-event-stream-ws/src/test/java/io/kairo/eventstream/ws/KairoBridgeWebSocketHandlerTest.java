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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.bridge.BridgeRequest;
import io.kairo.api.bridge.BridgeRequestHandler;
import io.kairo.api.bridge.BridgeResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration tests covering the five ops the v1.1 bridge MUST support: {@code agent.run}, {@code
 * agent.cancel}, {@code agent.status}, {@code tool.approve}, {@code workspace.list}.
 *
 * <p>Each test feeds an envelope through a real {@link KairoBridgeWebSocketHandler}, asserts that
 * the handler is invoked with the correct request shape, and that the response frame is correctly
 * correlated to the inbound {@code requestId}.
 */
class KairoBridgeWebSocketHandlerTest {

    private static final TypeReference<Map<String, Object>> MAP_OF_OBJECT =
            new TypeReference<>() {};

    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------ op 1: agent.run
    @Test
    void agentRun_dispatchesPayload_andCorrelatesResponse() throws Exception {
        RecordingHandler handler =
                new RecordingHandler(
                        req -> BridgeResponse.ok(Map.of("runId", "run-42", "status", "started")));
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(handler, mapper);

        String inbound =
                """
                {"requestId":"req-1","op":"agent.run",
                 "payload":{"agent":"summarizer","input":"hello"},
                 "meta":{"sessionId":"s-1"}}""";

        List<Map<String, Object>> sent = roundTrip(ws, inbound);

        assertThat(handler.requests).hasSize(1);
        BridgeRequest captured = handler.requests.get(0);
        assertThat(captured.op()).isEqualTo("agent.run");
        assertThat(captured.payload()).containsEntry("agent", "summarizer");
        assertThat(captured.meta().requestId()).isEqualTo("req-1");
        assertThat(captured.meta().attributes()).containsEntry("sessionId", "s-1");

        assertThat(sent).hasSize(1);
        Map<String, Object> resp = sent.get(0);
        assertThat(resp).containsEntry("requestId", "req-1");
        assertThat(resp).containsEntry("status", 200);
        assertThat(asMap(resp.get("payload"))).containsEntry("runId", "run-42");
    }

    // ------------------------------------------------------------------ op 2: agent.cancel
    @Test
    void agentCancel_returnsOk_evenWhenAlreadyTerminated() throws Exception {
        RecordingHandler handler =
                new RecordingHandler(req -> BridgeResponse.ok(Map.of("cancelled", true)));
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(handler, mapper);

        String inbound =
                """
                {"requestId":"cancel-1","op":"agent.cancel",
                 "payload":{"runId":"run-42"}}""";

        List<Map<String, Object>> sent = roundTrip(ws, inbound);

        assertThat(handler.requests.get(0).op()).isEqualTo("agent.cancel");
        assertThat(handler.requests.get(0).payload()).containsEntry("runId", "run-42");
        assertThat(sent.get(0)).containsEntry("requestId", "cancel-1");
        assertThat(sent.get(0)).containsEntry("status", 200);
        assertThat(asMap(sent.get(0).get("payload"))).containsEntry("cancelled", true);
    }

    // ------------------------------------------------------------------ op 3: agent.status
    @Test
    void agentStatus_synthesizesRequestId_whenClientOmits() throws Exception {
        RecordingHandler handler =
                new RecordingHandler(
                        req -> BridgeResponse.ok(Map.of("runId", "run-42", "phase", "tool-call")));
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(handler, mapper);

        // No requestId field — server MUST synthesize one and reflect it on the response.
        String inbound =
                """
                {"op":"agent.status","payload":{"runId":"run-42"}}""";

        List<Map<String, Object>> sent = roundTrip(ws, inbound);

        BridgeRequest captured = handler.requests.get(0);
        assertThat(captured.op()).isEqualTo("agent.status");
        assertThat(captured.meta().requestId()).isNotBlank();

        Map<String, Object> resp = sent.get(0);
        assertThat(resp.get("requestId")).isEqualTo(captured.meta().requestId());
        assertThat(resp).containsEntry("status", 200);
        assertThat(asMap(resp.get("payload"))).containsEntry("phase", "tool-call");
    }

    // ------------------------------------------------------------------ op 4: tool.approve
    @Test
    void toolApprove_propagatesDecisionToHandler() throws Exception {
        RecordingHandler handler =
                new RecordingHandler(req -> BridgeResponse.ok(Map.of("acked", true)));
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(handler, mapper);

        String inbound =
                """
                {"requestId":"approve-1","op":"tool.approve",
                 "payload":{"toolCallId":"tc-7","decision":"approve","reason":"trusted-script"}}""";

        List<Map<String, Object>> sent = roundTrip(ws, inbound);

        BridgeRequest captured = handler.requests.get(0);
        assertThat(captured.op()).isEqualTo("tool.approve");
        assertThat(captured.payload()).containsEntry("decision", "approve");
        assertThat(captured.payload()).containsEntry("toolCallId", "tc-7");

        assertThat(sent.get(0)).containsEntry("requestId", "approve-1");
        assertThat(sent.get(0)).containsEntry("status", 200);
    }

    // ------------------------------------------------------------------ op 5: workspace.list
    @Test
    void workspaceList_returnsArrayPayload() throws Exception {
        RecordingHandler handler =
                new RecordingHandler(
                        req ->
                                BridgeResponse.ok(
                                        Map.of(
                                                "workspaces",
                                                List.of(
                                                        Map.of("id", "wsA", "kind", "LOCAL"),
                                                        Map.of("id", "wsB", "kind", "LOCAL")))));
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(handler, mapper);

        String inbound =
                """
                {"requestId":"list-1","op":"workspace.list","payload":{}}""";

        List<Map<String, Object>> sent = roundTrip(ws, inbound);

        assertThat(handler.requests.get(0).op()).isEqualTo("workspace.list");
        Map<String, Object> resp = sent.get(0);
        assertThat(resp).containsEntry("requestId", "list-1");
        assertThat(resp).containsEntry("status", 200);
        Object workspaces = asMap(resp.get("payload")).get("workspaces");
        assertThat(workspaces).isInstanceOf(List.class);
        assertThat(((List<?>) workspaces)).hasSize(2);
    }

    // ------------------------------------------------------------------ error coverage
    @Test
    void unknownOp_resolvesToFourOhFour() throws Exception {
        RecordingHandler handler = new RecordingHandler(req -> BridgeResponse.notFound(req.op()));
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(handler, mapper);

        List<Map<String, Object>> sent =
                roundTrip(ws, "{\"requestId\":\"nf-1\",\"op\":\"unknown.op\"}");

        assertThat(sent.get(0)).containsEntry("requestId", "nf-1");
        assertThat(sent.get(0)).containsEntry("status", 404);
    }

    @Test
    void malformedFrame_resolvesToFourHundred() throws Exception {
        RecordingHandler handler = new RecordingHandler(req -> BridgeResponse.ok());
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(handler, mapper);

        List<Map<String, Object>> sent = roundTrip(ws, "not json at all");
        assertThat(handler.requests).isEmpty();
        assertThat(sent.get(0)).containsEntry("status", 400);
    }

    @Test
    void handlerThrows_resolvesToFiveHundred() throws Exception {
        BridgeRequestHandler boom =
                req -> {
                    throw new RuntimeException("kapow");
                };
        KairoBridgeWebSocketHandler ws = new KairoBridgeWebSocketHandler(boom, mapper);

        List<Map<String, Object>> sent =
                roundTrip(ws, "{\"requestId\":\"err-1\",\"op\":\"agent.run\"}");
        assertThat(sent.get(0)).containsEntry("requestId", "err-1");
        assertThat(sent.get(0)).containsEntry("status", 500);
    }

    // ----------------------------------------------------------------- helpers
    /**
     * Drives one inbound text frame through the handler and captures every outbound text frame as a
     * decoded JSON map.
     */
    private List<Map<String, Object>> roundTrip(KairoBridgeWebSocketHandler ws, String inbound) {
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo hs =
                new HandshakeInfo(
                        URI.create("ws://host/bridge"), new HttpHeaders(), Mono.empty(), null);
        when(session.getHandshakeInfo()).thenReturn(hs);

        DefaultDataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
        WebSocketMessage inboundMsg =
                new WebSocketMessage(
                        WebSocketMessage.Type.TEXT,
                        bufferFactory.wrap(inbound.getBytes(StandardCharsets.UTF_8)));
        when(session.receive()).thenReturn(Flux.just(inboundMsg));
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

        ws.handle(session).block();

        List<Map<String, Object>> decoded = new ArrayList<>();
        for (WebSocketMessage m : sent) {
            try {
                decoded.add(mapper.readValue(m.getPayloadAsText(), MAP_OF_OBJECT));
            } catch (Exception e) {
                throw new AssertionError("non-JSON outbound frame: " + m.getPayloadAsText(), e);
            }
        }
        return decoded;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new AssertionError(
                "expected Map, got " + (value == null ? "null" : value.getClass()));
    }

    /** Minimal {@link BridgeRequestHandler} that records every call and applies a fixed reply. */
    static final class RecordingHandler implements BridgeRequestHandler {
        final List<BridgeRequest> requests = new ArrayList<>();
        private final java.util.function.Function<BridgeRequest, BridgeResponse> reply;

        RecordingHandler(java.util.function.Function<BridgeRequest, BridgeResponse> reply) {
            this.reply = reply;
        }

        @Override
        public Mono<BridgeResponse> handle(BridgeRequest request) {
            requests.add(request);
            return Mono.just(reply.apply(request));
        }
    }
}
