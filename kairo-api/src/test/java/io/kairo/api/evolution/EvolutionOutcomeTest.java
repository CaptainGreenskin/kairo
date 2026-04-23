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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.memory.MemoryEntry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvolutionOutcomeTest {

    private static EvolvedSkill dummySkill() {
        return new EvolvedSkill("s", null, null, "instr", null, null, null, null, null, null, 0);
    }

    @Test
    @DisplayName("empty() returns outcome with no changes")
    void emptyOutcome() {
        EvolutionOutcome outcome = EvolutionOutcome.empty();

        assertTrue(outcome.skillToCreate().isEmpty());
        assertTrue(outcome.skillToPatch().isEmpty());
        assertTrue(outcome.memoriesToSave().isEmpty());
        assertEquals("", outcome.reviewNotes());
    }

    @Test
    @DisplayName("hasChanges returns false for empty outcome")
    void hasChangesReturnsFalseForEmpty() {
        assertFalse(EvolutionOutcome.empty().hasChanges());
    }

    @Test
    @DisplayName("hasChanges returns true when skillToCreate is present")
    void hasChangesReturnsTrueForSkillCreate() {
        EvolutionOutcome outcome =
                new EvolutionOutcome(
                        Optional.of(dummySkill()), Optional.empty(), List.of(), "created");

        assertTrue(outcome.hasChanges());
    }

    @Test
    @DisplayName("hasChanges returns true when skillToPatch is present")
    void hasChangesReturnsTrueForSkillPatch() {
        EvolutionOutcome outcome =
                new EvolutionOutcome(
                        Optional.empty(), Optional.of(dummySkill()), List.of(), "patched");

        assertTrue(outcome.hasChanges());
    }

    @Test
    @DisplayName("hasChanges returns true when memoriesToSave is non-empty")
    void hasChangesReturnsTrueForMemories() {
        MemoryEntry mem = MemoryEntry.session("m1", "some content", Set.of("tag"));
        EvolutionOutcome outcome =
                new EvolutionOutcome(Optional.empty(), Optional.empty(), List.of(mem), "memories");

        assertTrue(outcome.hasChanges());
    }

    @Test
    @DisplayName("Compact constructor defaults null Optional fields to empty")
    void compactConstructorDefaultsOptionals() {
        EvolutionOutcome outcome = new EvolutionOutcome(null, null, null, "notes");

        assertTrue(outcome.skillToCreate().isEmpty());
        assertTrue(outcome.skillToPatch().isEmpty());
        assertTrue(outcome.memoriesToSave().isEmpty());
        assertEquals("notes", outcome.reviewNotes());
    }
}
