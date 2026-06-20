/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.api.agent;

import io.kairo.api.Stable;
import io.kairo.api.message.Msg;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Core agent abstraction representing a single autonomous unit of work.
 *
 * <p>An agent receives a {@link Msg} and produces a response by orchestrating reasoning (via {@link
 * io.kairo.api.model.ModelProvider}), tool execution (via {@link io.kairo.api.tool.ToolExecutor}),
 * and context management (via {@link io.kairo.api.context.ContextManager}) in an iterative loop.
 *
 * <p>Agents are configured through {@link AgentConfig} and follow a defined lifecycle expressed by
 * {@link AgentState}. A typical usage pattern:
 *
 * <pre>{@code
 * Agent agent = runtime.createAgent(config);
 * Msg response = agent.call(Msg.user("Refactor the DAO layer")).block();
 * }</pre>
 *
 * <p><strong>Thread safety:</strong> Implementations are expected to be safe for concurrent {@link
 * #call(Msg)} invocations from different threads, though a single agent instance typically
 * processes one conversation at a time.
 *
 * @see AgentConfig
 * @see AgentState
 */
@Stable(value = "Core ReAct contract; shipped since v0.1 and unchanged", since = "1.0.0")
public interface Agent {

    /**
     * Process an input message and return the agent's response.
     *
     * <p>This is the primary entry point for driving agent behavior. The agent will iterate through
     * reasoning and acting phases until it produces a final response or reaches the configured
     * {@link AgentConfig#maxIterations()} limit.
     *
     * @param input the input message; must not be {@code null}
     * @return a {@link Mono} emitting the agent's response message, or an error signal if the agent
     *     fails or is interrupted
     */
    Mono<Msg> call(Msg input);

    /**
     * Returns the unique identifier of this agent.
     *
     * <p>The ID is typically auto-generated and used internally for tracing, logging, and
     * multi-agent coordination.
     *
     * @return the agent ID, never {@code null}
     */
    String id();

    /**
     * Returns the human-readable name of this agent.
     *
     * <p>This corresponds to the {@code name} field in {@link AgentConfig} and is used in log
     * output, tracing spans, and multi-agent routing.
     *
     * @return the agent name, never {@code null}
     */
    String name();

    /**
     * Returns the total tokens consumed by this agent across all iterations. Resets on each {@link
     * #call} invocation for stateless proxies.
     *
     * @return total tokens used, or 0 if not tracked
     */
    default long totalTokensUsed() {
        return 0;
    }

    /**
     * Returns the current lifecycle state of this agent.
     *
     * @return the current {@link AgentState}, never {@code null}
     * @see AgentState
     */
    AgentState state();

    /**
     * Interrupt the agent's current processing.
     *
     * <p>Signals the agent to abort its reasoning/acting loop as soon as possible. The in-flight
     * {@link #call(Msg)} Mono will terminate with an error or a partial response, depending on the
     * implementation.
     */
    void interrupt();

    /**
     * Capture a snapshot of the agent's current runtime state.
     *
     * <p>The snapshot includes conversation history, iteration count, token usage, and lifecycle
     * state. Runtime dependencies (ModelProvider, ToolExecutor, etc.) are not included. The
     * snapshot can be restored via {@code AgentBuilder.restoreFrom(snapshot)}.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}. Agents that
     * support snapshotting should override this method.
     *
     * @return an immutable snapshot of the agent's state
     * @throws UnsupportedOperationException if the agent does not support snapshotting
     */
    default AgentSnapshot snapshot() {
        throw new UnsupportedOperationException(
                "Snapshot not supported by this agent implementation");
    }

    /**
     * Per-session diagnostics for this agent.
     *
     * <p>Returns a read-only view of the current session's diagnostic counters, timing, and tracing
     * metadata. The returned instance is only valid for the duration of the current {@link
     * #call(Msg)} invocation.
     *
     * <p>The default implementation returns {@code null}, indicating that diagnostics are not
     * available for this agent implementation.
     *
     * @return the diagnostics snapshot, or {@code null} if not supported
     * @since 1.2.0
     */
    default AgentDiagnostics diagnostics() {
        return null;
    }

    /**
     * Inject messages into the agent's conversation history mid-run ("steering").
     *
     * <p>Intended to be called from another thread while a {@link #call(Msg)} is in flight: the
     * injected messages are appended to the conversation and picked up at the next reasoning
     * iteration, letting an operator nudge or correct a running turn without interrupting it.
     *
     * <p>The default implementation is a no-op, so implementations that don't support steering are
     * unaffected. {@code DefaultReActAgent} overrides this.
     *
     * @param messages messages to append; {@code null} or empty is ignored
     * @since 1.3.0
     */
    default void injectMessages(List<Msg> messages) {
        // no-op by default
    }
}
