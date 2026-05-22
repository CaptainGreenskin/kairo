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
import io.kairo.api.acp.AcpInitializeResponse;
import io.kairo.api.acp.AcpNewSessionResponse;
import io.kairo.api.acp.AcpPromptResponse;
import io.kairo.api.acp.AcpSessionUpdate;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * End-to-end exercise of the stdio server: feed a stream of JSON-RPC lines into stdin, capture what
 * the server writes to stdout, assert the wire shape. Uses a fake {@link AcpAgent} so no real LLM
 * call happens.
 */
class AcpStdioServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonRpcLineCodec codec = new JsonRpcLineCodec(mapper);

    @Test
    void initializeReturnsAgentInfoAndCapabilities() throws Exception {
        FakeAcpAgent agent = new FakeAcpAgent();
        String input =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":1,\"clientInfo\":{\"name\":\"zed\",\"version\":\"0.150\"}}}\n";

        List<JsonNode> outputs = runServer(agent, input);

        assertThat(outputs).hasSize(1);
        JsonNode response = outputs.get(0);
        assertThat(response.path("id").asLong()).isEqualTo(1L);
        JsonNode result = response.path("result");
        assertThat(result.path("protocolVersion").asInt())
                .isEqualTo(AcpInitializeResponse.CURRENT_PROTOCOL_VERSION);
        assertThat(result.path("agentInfo").path("name").asText()).isEqualTo("test-agent");
        // textOnly() advertises loadSession=true so Zed's restore-last-chat flow doesn't error.
        assertThat(result.path("agentCapabilities").path("loadSession").asBoolean()).isTrue();
    }

    @Test
    void newSessionHandsBackId() throws Exception {
        FakeAcpAgent agent = new FakeAcpAgent();
        String input =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":"
                        + "{\"cwd\":\"/tmp/project\",\"mcpServers\":[]}}\n";

        List<JsonNode> outputs = runServer(agent, input);

        assertThat(outputs).hasSize(1);
        assertThat(outputs.get(0).path("result").path("sessionId").asText())
                .isEqualTo("fake-session-1");
    }

    @Test
    void promptStreamsSessionUpdateThenResult() throws Exception {
        FakeAcpAgent agent = new FakeAcpAgent();
        String input =
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"session/prompt\",\"params\":"
                        + "{\"sessionId\":\"s1\",\"prompt\":[{\"type\":\"text\",\"text\":\"hi\"}]}}\n";

        List<JsonNode> outputs = runServer(agent, input);

        // Two frames: one session/update notification + one final response.
        assertThat(outputs).hasSize(2);
        JsonNode update = outputs.get(0);
        assertThat(update.path("method").asText()).isEqualTo("session/update");
        assertThat(update.path("params").path("update").path("sessionUpdate").asText())
                .isEqualTo("agent_message_chunk");
        assertThat(
                        update.path("params")
                                .path("update")
                                .path("content")
                                .get(0)
                                .path("text")
                                .asText())
                .isEqualTo("echo: hi");

        JsonNode result = outputs.get(1);
        assertThat(result.path("id").asLong()).isEqualTo(3L);
        assertThat(result.path("result").path("stopReason").asText()).isEqualTo("end_turn");
    }

    @Test
    void unknownMethodReturnsMethodNotFound() throws Exception {
        FakeAcpAgent agent = new FakeAcpAgent();
        String input = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"session/fork\",\"params\":{}}\n";

        List<JsonNode> outputs = runServer(agent, input);

        assertThat(outputs).hasSize(1);
        assertThat(outputs.get(0).path("error").path("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void parseErrorReturnsErrorAndKeepsServing() throws Exception {
        FakeAcpAgent agent = new FakeAcpAgent();
        String input =
                "{not valid json\n"
                        + "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n";

        List<JsonNode> outputs = runServer(agent, input);

        assertThat(outputs).hasSize(2);
        assertThat(outputs.get(0).path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(outputs.get(1).path("id").asLong()).isEqualTo(1L);
    }

    /** Run the server against a single fixed input until EOF. */
    private List<JsonNode> runServer(AcpAgent agent, String input) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AcpStdioServer server = new AcpStdioServer(agent, in, out, codec);
        server.serve();
        return parseFrames(out.toString(StandardCharsets.UTF_8));
    }

    private List<JsonNode> parseFrames(String wire) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (String line : wire.split("\n")) {
            if (line.isBlank()) continue;
            out.add(mapper.readTree(line));
        }
        return out;
    }

    /** Predictable AcpAgent for assertion. */
    private static final class FakeAcpAgent implements AcpAgent {
        @Override
        public Mono<AcpInitializeResponse> initialize(
                io.kairo.api.acp.AcpInitializeRequest request) {
            return Mono.just(
                    new AcpInitializeResponse(
                            AcpInitializeResponse.CURRENT_PROTOCOL_VERSION,
                            new AcpImplementation("test-agent", "9.9.9"),
                            AcpCapabilities.textOnly()));
        }

        @Override
        public Mono<AcpNewSessionResponse> newSession(
                io.kairo.api.acp.AcpNewSessionRequest request) {
            return Mono.just(new AcpNewSessionResponse("fake-session-1"));
        }

        @Override
        public Mono<AcpPromptResponse> prompt(
                io.kairo.api.acp.AcpPromptRequest request,
                java.util.function.Consumer<AcpSessionUpdate> sessionUpdater) {
            StringBuilder sb = new StringBuilder();
            request.prompt()
                    .forEach(
                            block -> {
                                if (block instanceof io.kairo.api.acp.AcpContentBlock.Text t) {
                                    if (sb.length() > 0) sb.append(' ');
                                    sb.append(t.text());
                                }
                            });
            sessionUpdater.accept(
                    new AcpSessionUpdate.AgentMessageChunk(request.sessionId(), "echo: " + sb));
            return Mono.just(new AcpPromptResponse(AcpPromptResponse.StopReason.END_TURN));
        }
    }
}
