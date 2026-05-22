/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

import io.kairo.api.Experimental;
import java.util.function.Consumer;
import reactor.core.publisher.Mono;

/**
 * Server-side handler an ACP host implements. The {@code kairo-acp} module routes incoming JSON-RPC
 * methods to the matching method on this interface.
 *
 * <p>v1.3 covers: {@link #initialize}, {@link #newSession}, {@link #prompt}, {@link #cancel},
 * {@link #loadSession}. Optional methods ({@code resume}, {@code fork}, {@code list}, {@code
 * set_model}, {@code set_mode}, {@code set_config_option}, plus client-bound {@code fs/*}, {@code
 * terminal/*}, {@code session/request_permission}) are still stubbed.
 *
 * <p>Push-side: {@link #prompt} receives a {@code sessionUpdater} the implementation invokes for
 * every streaming event. The stdio server serializes those into {@code session/update}
 * notifications back to the editor.
 *
 * @since 1.3 (Experimental)
 */
@Experimental("ACP SPI — contract may change in v1.x")
public interface AcpAgent {

    /** Editor connect handshake. */
    Mono<AcpInitializeResponse> initialize(AcpInitializeRequest request);

    /** Editor opens a new conversation. Returns the session id the editor will then prompt. */
    Mono<AcpNewSessionResponse> newSession(AcpNewSessionRequest request);

    /**
     * Run one user prompt to completion. Implementations push streaming text / thoughts / tool
     * events through {@code sessionUpdater}, then complete the returned Mono with a terminal {@link
     * AcpPromptResponse} carrying the stop reason.
     */
    Mono<AcpPromptResponse> prompt(
            AcpPromptRequest request, Consumer<AcpSessionUpdate> sessionUpdater);

    /**
     * Editor asks the agent to stop the in-flight prompt for {@code sessionId}. Implementations
     * should signal cancellation to the underlying agent ASAP; the in-flight {@link #prompt} call
     * should complete with {@link AcpPromptResponse.StopReason#CANCELLED}.
     *
     * <p>Default: no-op (agent ignores cancellation).
     */
    default Mono<Void> cancel(String sessionId) {
        return Mono.empty();
    }

    /**
     * Editor reopens a previously-stored session. Default: returns an empty session — agents that
     * persist session state should override.
     */
    default Mono<AcpNewSessionResponse> loadSession(String sessionId) {
        return Mono.just(new AcpNewSessionResponse(sessionId));
    }
}
