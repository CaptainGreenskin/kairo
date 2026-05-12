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

import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the current turn state, provided to continuation strategies for
 * decision-making.
 *
 * @param agentName the name of the agent executing the loop
 * @param iteration current iteration index (zero-based)
 * @param maxIterations configured maximum iterations for this session
 * @param conversationHistory the full conversation history up to this point
 * @param lastAssistantMsg the most recent assistant message that triggered evaluation
 * @param stopReason why the model stopped generating
 * @param pressure context window pressure ratio (0.0 = empty, 1.0 = full)
 * @param nudgesAppliedThisSession number of nudges already injected in this session
 * @param isPlanMode whether the agent is currently in plan/think mode
 * @param toolCallsInLastKIterations number of tool calls in the last K iterations
 * @param extensionData arbitrary strategy-scoped data for stateful strategies
 * @since 0.5.0
 */
public record ContinuationContext(
        String agentName,
        int iteration,
        int maxIterations,
        List<Msg> conversationHistory,
        Msg lastAssistantMsg,
        ModelResponse.StopReason stopReason,
        float pressure,
        int nudgesAppliedThisSession,
        boolean isPlanMode,
        int toolCallsInLastKIterations,
        Map<String, Object> extensionData) {

    /**
     * Whether the nudge budget allows another nudge.
     *
     * @param maxNudges the maximum number of nudges allowed per session
     * @return true if the nudge count is below the limit
     */
    public boolean withinNudgeBudget(int maxNudges) {
        return nudgesAppliedThisSession < maxNudges;
    }

    /**
     * Whether iteration budget has room for at least one more turn.
     *
     * @return true if current iteration is below maxIterations - 1
     */
    public boolean hasIterationBudget() {
        return iteration < maxIterations - 1;
    }
}
