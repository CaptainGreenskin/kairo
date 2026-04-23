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
package io.kairo.api.evolution;

import io.kairo.api.Experimental;
import io.kairo.api.message.Msg;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of the agent's state passed to evolution policies and triggers.
 *
 * @param agentName the name of the agent being evolved
 * @param conversationHistory recent conversation messages
 * @param iterationCount current iteration count in the ReAct loop
 * @param counters monotonic evolution counters
 * @param memoryReviewThreshold threshold for triggering memory review
 * @param skillReviewThreshold threshold for triggering skill review
 * @param tokensUsed total tokens consumed so far
 * @param existingSkills currently registered evolved skills
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public record EvolutionContext(
        String agentName,
        List<Msg> conversationHistory,
        int iterationCount,
        EvolutionCounters counters,
        int memoryReviewThreshold,
        int skillReviewThreshold,
        long tokensUsed,
        List<EvolvedSkill> existingSkills) {

    public EvolutionContext {
        Objects.requireNonNull(agentName, "agentName must not be null");
        conversationHistory =
                conversationHistory != null ? List.copyOf(conversationHistory) : List.of();
        counters = counters != null ? counters : EvolutionCounters.ZERO;
        existingSkills = existingSkills != null ? List.copyOf(existingSkills) : List.of();
    }
}
