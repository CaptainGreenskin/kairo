/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.acp.AcpContentBlock;
import io.kairo.api.acp.AcpInitializeRequest;
import io.kairo.api.acp.AcpNewSessionRequest;
import io.kairo.api.acp.AcpPromptRequest;
import io.kairo.api.acp.AcpPromptResponse;
import io.kairo.api.acp.AcpSessionUpdate;
import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultAcpAgentTest {

    @Test
    void newSessionAssignsUniqueIds() {
        DefaultAcpAgent agent = new DefaultAcpAgent(new EchoAgent());
        var a = agent.newSession(new AcpNewSessionRequest("/proj1", List.of())).block();
        var b = agent.newSession(new AcpNewSessionRequest("/proj2", List.of())).block();
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        assertThat(a.sessionId()).isNotEqualTo(b.sessionId());
        assertThat(agent.sessions().sessionCount()).isEqualTo(2);
    }

    @Test
    void promptForUnknownSessionRefusesGracefully() {
        DefaultAcpAgent agent = new DefaultAcpAgent(new EchoAgent());
        AcpPromptRequest req =
                new AcpPromptRequest("nonexistent", List.of(new AcpContentBlock.Text("hi")));
        AcpPromptResponse resp = agent.prompt(req, u -> {}).block();
        assertThat(resp).isNotNull();
        assertThat(resp.stopReason()).isEqualTo(AcpPromptResponse.StopReason.REFUSAL);
    }

    @Test
    void promptStreamsAgentMessageChunkAndCompletes() {
        DefaultAcpAgent agent = new DefaultAcpAgent(new EchoAgent());
        var sessionResp = agent.newSession(new AcpNewSessionRequest("/p", List.of())).block();
        assertThat(sessionResp).isNotNull();

        AcpPromptRequest req =
                new AcpPromptRequest(
                        sessionResp.sessionId(),
                        List.of(
                                new AcpContentBlock.Text("hello"),
                                new AcpContentBlock.Text("world")));
        List<AcpSessionUpdate> updates = new ArrayList<>();
        AcpPromptResponse resp = agent.prompt(req, updates::add).block();

        assertThat(resp).isNotNull();
        assertThat(resp.stopReason()).isEqualTo(AcpPromptResponse.StopReason.END_TURN);
        assertThat(updates).hasSize(1);
        var chunk = (AcpSessionUpdate.AgentMessageChunk) updates.get(0);
        assertThat(chunk.text()).isEqualTo("you said: hello\nworld");
    }

    @Test
    void resourceLinkRendersIntoUserText() {
        RecordingAgent recorder = new RecordingAgent();
        DefaultAcpAgent agent = new DefaultAcpAgent(recorder);
        var sess = agent.newSession(new AcpNewSessionRequest("/p", List.of())).block();
        assertThat(sess).isNotNull();

        agent.prompt(
                        new AcpPromptRequest(
                                sess.sessionId(),
                                List.of(
                                        new AcpContentBlock.Text("look at"),
                                        new AcpContentBlock.ResourceLink(
                                                "file:///tmp/x.py", "text/python"))),
                        u -> {})
                .block();

        assertThat(recorder.lastUserText)
                .contains("look at")
                .contains("[resource: file:///tmp/x.py]");
    }

    @Test
    void initializeReportsTextOnlyCapabilitiesByDefault() {
        DefaultAcpAgent agent = new DefaultAcpAgent(new EchoAgent());
        var resp =
                agent.initialize(
                                new AcpInitializeRequest(
                                        1, new io.kairo.api.acp.AcpImplementation("zed", "0.150")))
                        .block();
        assertThat(resp).isNotNull();
        assertThat(resp.agentCapabilities().promptImage()).isFalse();
        assertThat(resp.agentCapabilities().sessionFork()).isFalse();
    }

    @Test
    void agentExceptionEmitsErrorChunkAndCompletesEndTurn() {
        // ACP wire spec only allows end_turn / max_tokens / max_turn_requests / refusal /
        // cancelled — there is no "error" stop reason. When the wrapped Agent throws, we
        // surface the error as an AgentMessageChunk + complete with END_TURN so strict
        // editors (Zed) accept the response.
        DefaultAcpAgent agent = new DefaultAcpAgent(new FailingAgent());
        var sess = agent.newSession(new AcpNewSessionRequest("/p", List.of())).block();
        assertThat(sess).isNotNull();

        List<AcpSessionUpdate> updates = new ArrayList<>();
        AcpPromptResponse resp =
                agent.prompt(
                                new AcpPromptRequest(
                                        sess.sessionId(),
                                        List.of(new AcpContentBlock.Text("trigger fail"))),
                                updates::add)
                        .block();

        assertThat(resp).isNotNull();
        assertThat(resp.stopReason()).isEqualTo(AcpPromptResponse.StopReason.END_TURN);
        var chunk = (AcpSessionUpdate.AgentMessageChunk) updates.get(0);
        assertThat(chunk.text()).startsWith("[error]");
    }

    // ---- fakes ----

    /** Base stub implementing the boilerplate Agent methods we don't care about in this test. */
    private abstract static class StubAgent implements Agent {
        @Override
        public String id() {
            return "stub-" + System.identityHashCode(this);
        }

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public io.kairo.api.agent.AgentState state() {
            return io.kairo.api.agent.AgentState.IDLE;
        }

        @Override
        public void interrupt() {}
    }

    private static final class EchoAgent extends StubAgent {
        @Override
        public Mono<Msg> call(Msg input) {
            String userText =
                    input.contents().get(0) instanceof io.kairo.api.message.Content.TextContent t
                            ? t.text()
                            : "";
            return Mono.just(Msg.of(MsgRole.ASSISTANT, "you said: " + userText));
        }
    }

    private static final class RecordingAgent extends StubAgent {
        String lastUserText = "";

        @Override
        public Mono<Msg> call(Msg input) {
            if (input.contents().get(0) instanceof io.kairo.api.message.Content.TextContent t) {
                lastUserText = t.text();
            }
            return Mono.just(Msg.of(MsgRole.ASSISTANT, "ok"));
        }
    }

    private static final class FailingAgent extends StubAgent {
        @Override
        public Mono<Msg> call(Msg input) {
            return Mono.error(new RuntimeException("simulated upstream failure"));
        }
    }
}
