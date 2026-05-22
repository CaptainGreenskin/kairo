/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

import java.util.Map;

/**
 * Sealed family of events the agent pushes during a {@code session/prompt} call via the {@code
 * session/update} notification.
 *
 * <p>v1.3 covers:
 *
 * <ul>
 *   <li>{@link AgentMessageChunk} — incremental assistant text the editor appends to the current
 *       message bubble. Multiple chunks per turn supported.
 *   <li>{@link AgentThoughtChunk} — incremental "thinking" text shown in a separate pane.
 *   <li>{@link ToolCallStart} — tool invocation began; editor shows a "running" card.
 *   <li>{@link ToolCallProgress} — incremental output of a long-running tool.
 *   <li>{@link ToolCallComplete} — tool finished; editor stops the spinner and renders result.
 * </ul>
 */
public sealed interface AcpSessionUpdate {

    String sessionId();

    record AgentMessageChunk(String sessionId, String text) implements AcpSessionUpdate {}

    record AgentThoughtChunk(String sessionId, String text) implements AcpSessionUpdate {}

    /**
     * Tool call started. {@code toolCallId} is the agent-assigned id ACP uses to correlate
     * subsequent progress / complete events. {@code title} is a short human-readable label (e.g.
     * "Read file", "Run tests"). {@code input} is the tool's structured arguments.
     */
    record ToolCallStart(
            String sessionId, String toolCallId, String title, Map<String, Object> input)
            implements AcpSessionUpdate {}

    /** Incremental output of a still-running tool (e.g. lines of test output). Editors append. */
    record ToolCallProgress(String sessionId, String toolCallId, String chunk)
            implements AcpSessionUpdate {}

    /**
     * Tool finished. {@code success} drives the card's red/green state. {@code output} is the final
     * rendered text shown in the card body.
     */
    record ToolCallComplete(String sessionId, String toolCallId, boolean success, String output)
            implements AcpSessionUpdate {}
}
