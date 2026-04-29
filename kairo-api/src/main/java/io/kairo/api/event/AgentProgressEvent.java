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
package io.kairo.api.event;

import io.kairo.api.Experimental;

/**
 * Domain event published at key milestones of the ReAct agent loop.
 *
 * <p>Emitted onto {@link KairoEventBus} by {@code DefaultReActAgent}. Consumers (CLI progress
 * display, OTel exporter, monitoring dashboards) subscribe to these events to track iteration
 * count, tool invocations, and elapsed time without polling agent state.
 *
 * @param agentName name of the agent emitting the event
 * @param phase lifecycle phase within the ReAct loop
 * @param iteration current loop iteration (1-based)
 * @param detail tool name for TOOL_CALL/TOOL_RESULT; summary text for ITERATION_END/AGENT_DONE
 * @param elapsedMs milliseconds since {@code agent.run()} was called
 * @param inputTokens cumulative input tokens (0 if unknown)
 * @param outputTokens cumulative output tokens (0 if unknown)
 * @since v0.10 (Experimental)
 */
@Experimental("Agent progress events — contract may change in v0.11")
public record AgentProgressEvent(
        String agentName,
        Phase phase,
        int iteration,
        String detail,
        long elapsedMs,
        int inputTokens,
        int outputTokens) {

    /** ReAct loop lifecycle phases. */
    public enum Phase {
        /** Start of a new ReAct iteration, before the reasoning (LLM) call. */
        ITERATION_START,
        /** A tool call has been requested by the model. */
        TOOL_CALL,
        /** A tool call has completed with a result. */
        TOOL_RESULT,
        /** An iteration has completed (reasoning + all tool calls). */
        ITERATION_END,
        /** The agent has finished the entire session (success or failure). */
        AGENT_DONE
    }

    /** Domain tag used when wrapping this event into a {@link KairoEvent} for the bus. */
    public static final String DOMAIN_AGENT = "agent";

    /** Wrap this event into a {@link KairoEvent} suitable for {@link KairoEventBus#publish}. */
    public KairoEvent toKairoEvent() {
        return KairoEvent.wrap(DOMAIN_AGENT, phase.name(), this);
    }
}
