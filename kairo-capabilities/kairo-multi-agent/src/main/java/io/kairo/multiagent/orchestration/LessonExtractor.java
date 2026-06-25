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
package io.kairo.multiagent.orchestration;

import io.kairo.api.team.TeamResult.StepOutcome;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Extracts durable lessons from a completed team execution for cross-task self-evolution.
 *
 * <p>Implementations (typically backed by an LLM call) inspect each role's step outcomes and
 * produce {@link ExpertMemoryEntry} lessons to persist via {@link ExpertMemoryStore}. The framework
 * does not ship an LLM-backed extractor; the host application (e.g. kairo-code) injects one.
 *
 * <p>When no extractor is configured on {@link ExpertTeamCoordinator}, self-evolution write-back is
 * disabled (read-side recall still works against any pre-existing memories).
 *
 * @since v0.10 (Experimental)
 */
@FunctionalInterface
public interface LessonExtractor {

    /**
     * Extract lessons for a single role from that role's step outcomes.
     *
     * @param roleId the expert role identifier (e.g. "expert:coder")
     * @param roleOutcomes the outcomes produced by steps assigned to this role (may be empty)
     * @param goal the team execution goal (context for extraction)
     * @return a Mono emitting the extracted lessons (empty list = nothing to learn)
     */
    Mono<List<ExpertMemoryEntry>> extract(
            String roleId, List<StepOutcome> roleOutcomes, String goal);
}
