/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import io.kairo.api.acp.AcpSessionUpdate;
import java.util.function.Consumer;

/**
 * Optional streaming hook that {@link DefaultAcpAgent} consults during a {@code session/prompt}
 * call. Implementations represent the host's per-call delta source (e.g. kairo-assistant's {@code
 * SessionAwareDeltaRouter#consumerFor(sessionId)}).
 *
 * <p>When a {@code StreamingAcpBridge} is supplied, {@link DefaultAcpAgent} subscribes its own
 * {@link Consumer Consumer&lt;AcpSessionUpdate&gt;} into the bridge before calling the underlying
 * agent. The bridge is expected to forward each text delta as an {@link
 * AcpSessionUpdate.AgentMessageChunk}; any thoughts / tool-call events the host has access to may
 * be forwarded as the corresponding session-update records.
 *
 * <p>Hosts without a streaming surface (most agents) simply don't pass a bridge — the agent still
 * works, just with a single {@code AgentMessageChunk} per turn carrying the full response.
 */
@FunctionalInterface
public interface StreamingAcpBridge {

    /**
     * Subscribe {@code sink} to per-call deltas for {@code sessionId}. The implementation should
     * (a) start forwarding text deltas as {@link AcpSessionUpdate.AgentMessageChunk} events, and
     * (b) return an idempotent unsubscribe callback for the agent call's completion / cancellation
     * to invoke.
     */
    AutoCloseable subscribe(String sessionId, Consumer<AcpSessionUpdate> sink);
}
