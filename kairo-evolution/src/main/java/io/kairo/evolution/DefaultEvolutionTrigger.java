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
package io.kairo.evolution;

import io.kairo.api.evolution.EvolutionContext;
import io.kairo.api.evolution.EvolutionTrigger;

/**
 * Default threshold-based trigger that fires skill or memory reviews when monotonic counters exceed
 * the configured thresholds in the {@link EvolutionContext}.
 *
 * @since v0.9 (Experimental)
 */
public class DefaultEvolutionTrigger implements EvolutionTrigger {

    @Override
    public boolean shouldReviewSkill(EvolutionContext context) {
        return context.counters().toolLoopIterationsSinceLastSkillReview()
                >= context.skillReviewThreshold();
    }

    @Override
    public boolean shouldReviewMemory(EvolutionContext context) {
        return context.counters().turnSinceLastMemoryReview() >= context.memoryReviewThreshold();
    }

    @Override
    public String reason() {
        return "default-threshold-trigger";
    }
}
