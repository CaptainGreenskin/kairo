/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * Outbound {@code session/prompt} result — terminal envelope after all {@link AcpSessionUpdate}
 * notifications have been streamed.
 *
 * <p>{@link StopReason} mirrors the ACP wire enum exactly: {@code end_turn / max_tokens /
 * max_turn_requests / refusal / cancelled}. Agents that fail mid-call should emit an {@link
 * AcpSessionUpdate.AgentMessageChunk} with the error text and complete with {@link
 * StopReason#END_TURN} — the wire spec has no "error" variant, and editors that deserialize
 * strictly (Zed) will reject anything outside the set above.
 */
public record AcpPromptResponse(StopReason stopReason) {

    public enum StopReason {
        END_TURN,
        MAX_TOKENS,
        MAX_TURN_REQUESTS,
        REFUSAL,
        CANCELLED
    }
}
