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
 *   <li>{@link AcpContentBlock.ResourceLink} → {@code [resource: <uri>]} marker.
 *   <li>{@link AcpContentBlock.Image} / {@link AcpContentBlock.Audio} → {@code [image: mimeType]} /
 *       {@code [audio: mimeType]} markers (full base64 stays out of the conversation; agents
 *       wanting the bytes can dereference via a workspace tool).
 *   <li>{@link AcpContentBlock.EmbeddedResource} → text body inlined when present.
 * </ul>
 *
 * <p><strong>Streaming.</strong> When constructed with a {@link StreamingAcpBridge}, that bridge
 * subscribes a {@link Consumer Consumer&lt;AcpSessionUpdate&gt;} that forwards each delta into
 * ACP's {@code agent_message_chunk} stream. Without a bridge, the full response is emitted as a
 * single chunk on completion (the default for plain {@link Agent}s that don't expose a streaming
 * surface).
 *
 * <p><strong>Cancellation.</strong> {@link #cancel(String)} calls {@link Agent#interrupt()} on the
 * wrapped agent.
 */
public final class DefaultAcpAgent implements AcpAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultAcpAgent.class);

    private final Agent agent;
    private final AcpSessionManager sessions;
    private final AcpImplementation info;
    private final AcpCapabilities capabilities;
    private final StreamingAcpBridge streamingBridge;

    public DefaultAcpAgent(Agent agent) {
        this(
                agent,
                new AcpSessionManager(),
                new AcpImplementation("kairo", "0.0.0"),
                AcpCapabilities.textOnly(),
                null);
    }

    public DefaultAcpAgent(
            Agent agent,
            AcpSessionManager sessions,
            AcpImplementation info,
            AcpCapabilities capabilities) {
        this(agent, sessions, info, capabilities, null);
    }

    public DefaultAcpAgent(
            Agent agent,
            AcpSessionManager sessions,
            AcpImplementation info,
            AcpCapabilities capabilities,
            StreamingAcpBridge streamingBridge) {
        this.agent = agent;
        this.sessions = sessions;
        this.info = info;
        this.capabilities = capabilities;
        this.streamingBridge = streamingBridge;
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
    public Mono<AcpNewSessionResponse> loadSession(String sessionId) {
        return sessions.get(sessionId)
                .map(s -> Mono.just(new AcpNewSessionResponse(s.sessionId())))
                .orElseGet(
                        () -> {
                            sessions.put(new AcpSessionManager.AcpSessionState(sessionId, null));
                            return Mono.just(new AcpNewSessionResponse(sessionId));
                        });
    }

    @Override
    public Mono<Void> cancel(String sessionId) {
        log.info("ACP cancel session {}", sessionId);
        try {
            agent.interrupt();
        } catch (Exception e) {
            log.debug("Agent.interrupt() threw: {}", e.getMessage());
        }
        return Mono.empty();
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

        AutoCloseable streamHandle =
                streamingBridge == null
                        ? () -> {}
                        : streamingBridge.subscribe(request.sessionId(), sessionUpdater);

        Msg userMsg = Msg.of(MsgRole.USER, userText);
        return agent.call(userMsg)
                .map(
                        response -> {
                            String text = response == null ? "" : response.text();
                            if (streamingBridge == null && text != null && !text.isEmpty()) {
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
                        })
                .doFinally(
                        sig -> {
                            try {
                                streamHandle.close();
                            } catch (Exception ignore) {
                            }
                        });
    }

    private static String renderPromptToText(AcpPromptRequest request) {
        StringBuilder sb = new StringBuilder();
        for (AcpContentBlock block : request.prompt()) {
            if (sb.length() > 0) sb.append('\n');
            if (block instanceof AcpContentBlock.Text t) {
                sb.append(t.text() == null ? "" : t.text());
            } else if (block instanceof AcpContentBlock.ResourceLink rl) {
                sb.append("[resource: ").append(rl.uri()).append("]");
            } else if (block instanceof AcpContentBlock.Image img) {
                sb.append("[image: ").append(img.mimeType()).append("]");
            } else if (block instanceof AcpContentBlock.Audio audio) {
                sb.append("[audio: ").append(audio.mimeType()).append("]");
            } else if (block instanceof AcpContentBlock.EmbeddedResource er) {
                sb.append("[resource: ").append(er.uri()).append("]");
                if (er.text() != null && !er.text().isBlank()) {
                    sb.append('\n').append(er.text());
                }
            }
        }
        return sb.toString();
    }
}
