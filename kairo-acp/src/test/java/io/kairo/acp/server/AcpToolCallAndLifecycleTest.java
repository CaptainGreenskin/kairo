/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.acp.wire.JsonRpcLineCodec;
import io.kairo.api.acp.AcpAgent;
import io.kairo.api.acp.AcpCapabilities;
import io.kairo.api.acp.AcpImplementation;
import io.kairo.api.acp.AcpInitializeRequest;
import io.kairo.api.acp.AcpInitializeResponse;
import io.kairo.api.acp.AcpNewSessionRequest;
import io.kairo.api.acp.AcpNewSessionResponse;
import io.kairo.api.acp.AcpPromptRequest;
import io.kairo.api.acp.AcpPromptResponse;
import io.kairo.api.acp.AcpSessionUpdate;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Covers the v2 additions: tool-call mirror events, session/cancel routing, session/load routing,
 * and the image / audio / embedded-resource content block parsing.
 */
class AcpToolCallAndLifecycleTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonRpcLineCodec codec = new JsonRpcLineCodec(mapper);

    @Test
    void toolCallEventsSerializeToSessionUpdateNotifications() throws Exception {
        ToolCallEmittingAgent agent = new ToolCallEmittingAgent();
        String input =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/prompt\",\"params\":"
                        + "{\"sessionId\":\"s\",\"prompt\":[{\"type\":\"text\",\"text\":\"go\"}]}}\n";

        List<JsonNode> frames = runServer(agent, input);

        // 4 frames: tool_call_start + tool_call_progress + tool_call_complete + final result
        assertThat(frames).hasSize(4);
        assertThat(frames.get(0).path("params").path("update").path("sessionUpdate").asText())
                .isEqualTo("tool_call_start");
        assertThat(frames.get(0).path("params").path("update").path("toolCallId").asText())
                .isEqualTo("call-1");
        assertThat(frames.get(0).path("params").path("update").path("title").asText())
                .isEqualTo("Read file");
        assertThat(frames.get(1).path("params").path("update").path("sessionUpdate").asText())
                .isEqualTo("tool_call_progress");
        assertThat(frames.get(1).path("params").path("update").path("chunk").asText())
                .isEqualTo("line 1\n");
        assertThat(frames.get(2).path("params").path("update").path("sessionUpdate").asText())
                .isEqualTo("tool_call_complete");
        assertThat(frames.get(2).path("params").path("update").path("success").asBoolean())
                .isTrue();
        assertThat(frames.get(3).path("result").path("stopReason").asText()).isEqualTo("end_turn");
    }

    @Test
    void sessionCancelRoutesToAgentAndAcknowledges() throws Exception {
        CancellableAgent agent = new CancellableAgent();
        String input =
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"session/cancel\",\"params\":"
                        + "{\"sessionId\":\"s1\"}}\n";

        List<JsonNode> frames = runServer(agent, input);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).path("id").asLong()).isEqualTo(7L);
        assertThat(frames.get(0).has("error")).isFalse();
        assertThat(agent.cancelled.get()).isTrue();
    }

    @Test
    void sessionLoadResurrectsSessionId() throws Exception {
        LoadAwareAgent agent = new LoadAwareAgent();
        String input =
                "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"session/load\",\"params\":"
                        + "{\"sessionId\":\"abc-123\"}}\n";

        List<JsonNode> frames = runServer(agent, input);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).path("result").path("sessionId").asText()).isEqualTo("abc-123");
    }

    @Test
    void imageAndEmbeddedResourceBlocksParseWithoutFailure() throws Exception {
        ContentRecordingAgent agent = new ContentRecordingAgent();
        String input =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/prompt\",\"params\":"
                        + "{\"sessionId\":\"s\",\"prompt\":["
                        + "{\"type\":\"text\",\"text\":\"see this\"},"
                        + "{\"type\":\"image\",\"mimeType\":\"image/png\",\"data\":\"abc==\"},"
                        + "{\"type\":\"audio\",\"mimeType\":\"audio/wav\",\"data\":\"def==\"},"
                        + "{\"type\":\"resource\",\"uri\":\"file:///x.py\","
                        + "\"mimeType\":\"text/python\",\"text\":\"print('hi')\"}"
                        + "]}}\n";

        runServer(agent, input);

        assertThat(agent.received).hasSize(4);
        assertThat(agent.received.get(1))
                .isInstanceOf(io.kairo.api.acp.AcpContentBlock.Image.class);
        assertThat(agent.received.get(2))
                .isInstanceOf(io.kairo.api.acp.AcpContentBlock.Audio.class);
        assertThat(agent.received.get(3))
                .isInstanceOf(io.kairo.api.acp.AcpContentBlock.EmbeddedResource.class);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private List<JsonNode> runServer(AcpAgent agent, String input) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AcpStdioServer(agent, in, out, codec).serve();
        List<JsonNode> frames = new ArrayList<>();
        for (String line : out.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) frames.add(mapper.readTree(line));
        }
        return frames;
    }

    private static final class ToolCallEmittingAgent implements AcpAgent {
        @Override
        public Mono<AcpInitializeResponse> initialize(AcpInitializeRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<AcpNewSessionResponse> newSession(AcpNewSessionRequest request) {
            return Mono.just(new AcpNewSessionResponse("s"));
        }

        @Override
        public Mono<AcpPromptResponse> prompt(
                AcpPromptRequest request,
                java.util.function.Consumer<AcpSessionUpdate> sessionUpdater) {
            sessionUpdater.accept(
                    new AcpSessionUpdate.ToolCallStart(
                            request.sessionId(),
                            "call-1",
                            "Read file",
                            Map.of("path", "README.md")));
            sessionUpdater.accept(
                    new AcpSessionUpdate.ToolCallProgress(
                            request.sessionId(), "call-1", "line 1\n"));
            sessionUpdater.accept(
                    new AcpSessionUpdate.ToolCallComplete(
                            request.sessionId(), "call-1", true, "(complete)"));
            return Mono.just(new AcpPromptResponse(AcpPromptResponse.StopReason.END_TURN));
        }
    }

    private static final class CancellableAgent implements AcpAgent {
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public Mono<AcpInitializeResponse> initialize(AcpInitializeRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<AcpNewSessionResponse> newSession(AcpNewSessionRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<AcpPromptResponse> prompt(
                AcpPromptRequest request,
                java.util.function.Consumer<AcpSessionUpdate> sessionUpdater) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> cancel(String sessionId) {
            cancelled.set(true);
            return Mono.empty();
        }
    }

    private static final class LoadAwareAgent implements AcpAgent {
        @Override
        public Mono<AcpInitializeResponse> initialize(AcpInitializeRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<AcpNewSessionResponse> newSession(AcpNewSessionRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<AcpPromptResponse> prompt(
                AcpPromptRequest request,
                java.util.function.Consumer<AcpSessionUpdate> sessionUpdater) {
            return Mono.empty();
        }

        @Override
        public Mono<AcpNewSessionResponse> loadSession(String sessionId) {
            return Mono.just(new AcpNewSessionResponse(sessionId));
        }
    }

    private static final class ContentRecordingAgent implements AcpAgent {
        final List<io.kairo.api.acp.AcpContentBlock> received = new ArrayList<>();

        @Override
        public Mono<AcpInitializeResponse> initialize(AcpInitializeRequest request) {
            return Mono.just(
                    new AcpInitializeResponse(
                            AcpInitializeResponse.CURRENT_PROTOCOL_VERSION,
                            new AcpImplementation("rec", "0"),
                            AcpCapabilities.textOnly()));
        }

        @Override
        public Mono<AcpNewSessionResponse> newSession(AcpNewSessionRequest request) {
            return Mono.just(new AcpNewSessionResponse("s"));
        }

        @Override
        public Mono<AcpPromptResponse> prompt(
                AcpPromptRequest request,
                java.util.function.Consumer<AcpSessionUpdate> sessionUpdater) {
            received.addAll(request.prompt());
            return Mono.just(new AcpPromptResponse(AcpPromptResponse.StopReason.END_TURN));
        }
    }
}
