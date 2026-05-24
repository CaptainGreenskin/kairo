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
package io.kairo.expertteam.memory;

import java.time.Instant;

/**
 * A single lesson learned by an expert during a team execution.
 *
 * <p>Instances are produced by a batch extraction step at team completion (not per-step) and
 * persisted by {@link ExpertMemoryStore} for cross-task knowledge transfer.
 *
 * @param roleId the expert role identifier (e.g. "expert:coder"); must not be null or blank
 * @param namespace logical grouping key for memories (defaults to roleId if blank/null)
 * @param lesson the extracted lesson text; must not be null or blank
 * @param recordedAt when this lesson was recorded; defaults to now if null
 * @param relevanceScore relevance score in [0.0, 1.0] for recall ranking
 * @since v0.10 (Experimental)
 */
public record ExpertMemoryEntry(
        String roleId, String namespace, String lesson, Instant recordedAt, double relevanceScore) {

    /** Compact constructor — validates invariants and applies defaults. */
    public ExpertMemoryEntry {
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("roleId must not be null or blank");
        }
        if (lesson == null || lesson.isBlank()) {
            throw new IllegalArgumentException("lesson must not be null or blank");
        }
        if (relevanceScore < 0.0 || relevanceScore > 1.0) {
            throw new IllegalArgumentException(
                    "relevanceScore must be between 0.0 and 1.0, was: " + relevanceScore);
        }
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
        if (namespace == null || namespace.isBlank()) {
            namespace = roleId;
        }
    }
}
