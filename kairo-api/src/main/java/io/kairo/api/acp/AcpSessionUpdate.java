/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * Sealed family of events the agent pushes during a {@code session/prompt} call via the {@code
 * session/update} notification.
 *
 * <p>MVP covers the two events every editor actually renders:
 *
 * <ul>
 *   <li>{@link AgentMessageChunk} — incremental assistant text the editor appends to the current
 *       message bubble.
 *   <li>{@link AgentThoughtChunk} — incremental "thinking" text shown in a separate pane.
 * </ul>
 *
 * <p>Full ACP defines more (tool-call cards, plan updates, usage stats); add records to this sealed
 * family when needed.
 */
public sealed interface AcpSessionUpdate {

    String sessionId();

    record AgentMessageChunk(String sessionId, String text) implements AcpSessionUpdate {}

    record AgentThoughtChunk(String sessionId, String text) implements AcpSessionUpdate {}
}
