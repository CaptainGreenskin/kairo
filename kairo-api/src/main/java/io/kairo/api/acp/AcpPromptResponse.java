/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * Outbound {@code session/prompt} result — terminal envelope after all {@link AcpSessionUpdate}
 * notifications have been streamed.
 */
public record AcpPromptResponse(StopReason stopReason) {

    public enum StopReason {
        END_TURN,
        MAX_TOKENS,
        TOOL_USE,
        REFUSAL,
        CANCELLED,
        ERROR
    }
}
