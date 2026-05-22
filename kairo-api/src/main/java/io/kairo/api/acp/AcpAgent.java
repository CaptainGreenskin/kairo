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
 * <p>MVP covers three methods: {@link #initialize}, {@link #newSession}, {@link #prompt}. ACP's
 * remaining surface ({@code authenticate}, {@code session/load}, {@code session/resume}, {@code
 * session/fork}, {@code session/list}, {@code session/cancel}, {@code session/set_mode}, {@code
 * session/set_model}, {@code session/set_config_option}, plus client-bound {@code fs/*}, {@code
 * terminal/*}, {@code session/request_permission}) gets stubbed-not-supported responses until a
 * follow-up.
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
     * Run one user prompt to completion. Implementations push streaming text / thoughts through
     * {@code sessionUpdater} as they generate, then complete the returned Mono with a terminal
     * {@link AcpPromptResponse} carrying the stop reason.
     */
    Mono<AcpPromptResponse> prompt(
            AcpPromptRequest request, Consumer<AcpSessionUpdate> sessionUpdater);
}
