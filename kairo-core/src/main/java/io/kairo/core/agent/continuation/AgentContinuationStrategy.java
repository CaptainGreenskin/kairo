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
package io.kairo.core.agent.continuation;

import reactor.core.publisher.Mono;

/**
 * Decides whether the ReAct loop should continue when the model emits a turn with zero tool calls.
 * Pluggable via {@code AgentBuilder#continuationStrategy}.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Stateless or strictly per-session (strategies are shared across sessions by default — use
 *       {@link ContinuationContext#extensionData()} for scoped state)
 *   <li>Must complete within 5s of wall time (blocking calls forbidden)
 *   <li>Returning {@link ContinuationDecision.Nudge} appends a synthetic user message — frameworks
 *       MUST tag it so the UI bridge does not render it
 * </ul>
 *
 * @since 0.5.0
 */
public interface AgentContinuationStrategy {

    /**
     * Evaluate the current turn state and decide how the loop should proceed.
     *
     * @param ctx immutable snapshot of the current turn state
     * @return a {@link ContinuationDecision} indicating the next action
     */
    Mono<ContinuationDecision> decide(ContinuationContext ctx);

    /**
     * Human-readable name for this strategy, used in logging and diagnostics.
     *
     * @return the strategy name
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
