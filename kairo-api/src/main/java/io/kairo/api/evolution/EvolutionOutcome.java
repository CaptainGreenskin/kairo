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
import io.kairo.api.memory.MemoryEntry;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of an evolution review, describing what skills and memories should change.
 *
 * @param skillToCreate an optional new skill to create
 * @param skillToPatch an optional existing skill to update
 * @param memoriesToSave memory entries to persist
 * @param reviewNotes human-readable notes from the review process
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public record EvolutionOutcome(
        Optional<EvolvedSkill> skillToCreate,
        Optional<EvolvedSkill> skillToPatch,
        List<MemoryEntry> memoriesToSave,
        String reviewNotes) {

    public EvolutionOutcome {
        skillToCreate = skillToCreate != null ? skillToCreate : Optional.empty();
        skillToPatch = skillToPatch != null ? skillToPatch : Optional.empty();
        memoriesToSave = memoriesToSave != null ? List.copyOf(memoriesToSave) : List.of();
    }

    public boolean hasChanges() {
        return skillToCreate.isPresent() || skillToPatch.isPresent() || !memoriesToSave.isEmpty();
    }

    public static EvolutionOutcome empty() {
        return new EvolutionOutcome(Optional.empty(), Optional.empty(), List.of(), "");
    }
}
