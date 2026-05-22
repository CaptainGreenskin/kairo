/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import io.kairo.api.acp.AcpAgent;
import io.kairo.api.acp.AcpCapabilities;
import io.kairo.api.acp.AcpContentBlock;
import io.kairo.api.acp.AcpImplementation;
import io.kairo.api.acp.AcpInitializeRequest;
import io.kairo.api.acp.AcpInitializeResponse;
import io.kairo.api.acp.AcpNewSessionRequest;
import io.kairo.api.acp.AcpNewSessionResponse;
import io.kairo.api.acp.AcpPromptRequest;
import io.kairo.api.acp.AcpPromptResponse;
import io.kairo.api.acp.AcpSessionUpdate;
import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Bridge: wraps an existing {@link Agent} so the ACP server can drive it.
 *
 * <p>Per-call translation:
 *
 * <ul>
 *   <li>Concatenate every {@link AcpContentBlock.Text} in the prompt into a single user {@link
 *       Msg}.
 *   <li>Render {@link AcpContentBlock.ResourceLink} as a short {@code [resource: <uri>]} line
 *       appended to the text — MVP just surfaces the URI to the agent; full attachment fetch lands
 *       when ACP image / resource blocks are added.
 *   <li>Invoke {@link Agent#call(Msg)} and emit the response as ONE {@link
 *       AcpSessionUpdate.AgentMessageChunk}, then complete with {@code END_TURN}.
 * </ul>
 *
 * <p>Streaming of incremental text deltas is a planned follow-up — it requires either the agent
 * implementing a streaming surface or this bridge subscribing to a delta source like
 * kairo-assistant's {@code SessionAwareDeltaRouter}.
 */
public final class DefaultAcpAgent implements AcpAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultAcpAgent.class);

    private final Agent agent;
    private final AcpSessionManager sessions;
    private final AcpImplementation info;
    private final AcpCapabilities capabilities;

    public DefaultAcpAgent(Agent agent) {
        this(
                agent,
                new AcpSessionManager(),
                new AcpImplementation("kairo", "0.0.0"),
                AcpCapabilities.textOnly());
    }

    public DefaultAcpAgent(
            Agent agent,
            AcpSessionManager sessions,
            AcpImplementation info,
            AcpCapabilities capabilities) {
        this.agent = agent;
        this.sessions = sessions;
        this.info = info;
        this.capabilities = capabilities;
    }

    public AcpSessionManager sessions() {
        return sessions;
    }

    @Override
    public Mono<AcpInitializeResponse> initialize(AcpInitializeRequest request) {
        log.info(
                "ACP initialize from {} (protocol v{})",
                request.clientInfo() == null ? "unknown" : request.clientInfo().name(),
                request.protocolVersion());
        return Mono.just(
                new AcpInitializeResponse(
                        AcpInitializeResponse.CURRENT_PROTOCOL_VERSION, info, capabilities));
    }

    @Override
    public Mono<AcpNewSessionResponse> newSession(AcpNewSessionRequest request) {
        AcpSessionManager.AcpSessionState state = sessions.newSession(request.cwd());
        log.info("ACP new session {} cwd={}", state.sessionId(), state.cwd());
        return Mono.just(new AcpNewSessionResponse(state.sessionId()));
    }

    @Override
    public Mono<AcpPromptResponse> prompt(
            AcpPromptRequest request, Consumer<AcpSessionUpdate> sessionUpdater) {
        if (sessions.get(request.sessionId()).isEmpty()) {
            log.warn("ACP prompt for unknown session {}", request.sessionId());
            return Mono.just(new AcpPromptResponse(AcpPromptResponse.StopReason.REFUSAL));
        }

        String userText = renderPromptToText(request);
        if (userText.isBlank()) {
            return Mono.just(new AcpPromptResponse(AcpPromptResponse.StopReason.END_TURN));
        }

        Msg userMsg = Msg.of(MsgRole.USER, userText);
        return agent.call(userMsg)
                .map(
                        response -> {
                            String text = response == null ? "" : response.text();
                            if (text != null && !text.isEmpty()) {
                                sessionUpdater.accept(
                                        new AcpSessionUpdate.AgentMessageChunk(
                                                request.sessionId(), text));
                            }
                            return new AcpPromptResponse(AcpPromptResponse.StopReason.END_TURN);
                        })
                .onErrorResume(
                        e -> {
                            log.warn("ACP prompt failed: {}", e.getMessage(), e);
                            sessionUpdater.accept(
                                    new AcpSessionUpdate.AgentMessageChunk(
                                            request.sessionId(), "[error] " + e.getMessage()));
                            return Mono.just(
                                    new AcpPromptResponse(AcpPromptResponse.StopReason.ERROR));
                        });
    }

    private static String renderPromptToText(AcpPromptRequest request) {
        StringBuilder sb = new StringBuilder();
        for (AcpContentBlock block : request.prompt()) {
            if (block instanceof AcpContentBlock.Text t) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(t.text() == null ? "" : t.text());
            } else if (block instanceof AcpContentBlock.ResourceLink rl) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("[resource: ").append(rl.uri()).append("]");
            }
        }
        return sb.toString();
    }
}
