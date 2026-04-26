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

/**
 * Monotonic counters tracking evolution-related events within a session.
 *
 * @param turnSinceLastMemoryReview turns elapsed since the last memory review
 * @param toolLoopIterationsSinceLastSkillReview tool loop iterations since last skill review
 * @param consecutiveFailures consecutive failure count
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public record EvolutionCounters(
        int turnSinceLastMemoryReview,
        int toolLoopIterationsSinceLastSkillReview,
        int consecutiveFailures) {

    public static final EvolutionCounters ZERO = new EvolutionCounters(0, 0, 0);
}
