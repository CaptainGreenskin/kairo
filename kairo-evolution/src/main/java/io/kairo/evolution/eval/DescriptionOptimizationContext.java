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
package io.kairo.evolution.eval;

import java.util.List;

/**
 * Input handed to a {@link LlmDescriptionOptimizer}. Contains everything the LLM needs to propose a
 * better description without re-fetching state.
 *
 * @param skillName the skill's name (frontmatter identifier)
 * @param skillBody the full SKILL.md body — gives the LLM context about what the skill actually
 *     does so it can phrase the description faithfully
 * @param currentDescription the description that was just evaluated
 * @param failedTriggers queries that {@code shouldTrigger} but did not (failed to trigger)
 * @param falseTriggers queries that {@code !shouldTrigger} but did (false positives)
 * @param history every prior description tried and its train/test pass-rate. Lets the LLM avoid
 *     repeating a description that already failed.
 */
public record DescriptionOptimizationContext(
        String skillName,
        String skillBody,
        String currentDescription,
        List<SkillEvalResult> failedTriggers,
        List<SkillEvalResult> falseTriggers,
        List<HistoryEntry> history) {

    public DescriptionOptimizationContext {
        failedTriggers = List.copyOf(failedTriggers);
        falseTriggers = List.copyOf(falseTriggers);
        history = List.copyOf(history);
    }

    /** One past attempt: description + how it scored. */
    public record HistoryEntry(
            int iteration,
            String description,
            SkillEvalResult.Summary trainSummary,
            SkillEvalResult.Summary testSummary) {}
}
