/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

import java.util.List;

/**
 * Inbound {@code session/prompt} params. The editor passes a list of content blocks composing the
 * user's prompt; the agent streams back updates via {@link AcpSessionUpdate} notifications and ends
 * with an {@link AcpPromptResponse}.
 */
public record AcpPromptRequest(String sessionId, List<AcpContentBlock> prompt) {}
