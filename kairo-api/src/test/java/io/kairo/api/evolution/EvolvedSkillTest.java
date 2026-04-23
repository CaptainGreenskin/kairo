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

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvolvedSkillTest {

    private static EvolvedSkill minimal() {
        return new EvolvedSkill(
                "skill-1", null, null, "do something", null, null, null, null, null, null, 0);
    }

    @Test
    @DisplayName("Construct with required fields — optional fields can be null/default")
    void constructWithRequiredFields() {
        EvolvedSkill skill = minimal();

        assertEquals("skill-1", skill.name());
        assertEquals("do something", skill.instructions());
        assertNull(skill.version());
        assertNull(skill.description());
        assertNull(skill.category());
        assertEquals(Set.of(), skill.tags());
        assertNull(skill.trustLevel());
        assertNull(skill.metadata());
        assertNull(skill.createdAt());
        assertNull(skill.updatedAt());
        assertEquals(0, skill.usageCount());
    }

    @Test
    @DisplayName("Compact constructor rejects null name")
    void compactConstructorRejectsNullName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new EvolvedSkill(
                                null, null, null, "instr", null, null, null, null, null, null, 0));
    }

    @Test
    @DisplayName("Compact constructor rejects null instructions")
    void compactConstructorRejectsNullInstructions() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new EvolvedSkill(
                                "n", null, null, null, null, null, null, null, null, null, 0));
    }

    @Test
    @DisplayName("Compact constructor defensive-copies tags via Set.copyOf")
    void compactConstructorDefensiveCopiesTags() {
        Set<String> mutableTags = new HashSet<>(Set.of("a", "b"));
        EvolvedSkill skill =
                new EvolvedSkill(
                        "s", null, null, "i", null, mutableTags, null, null, null, null, 0);

        // mutating the original collection must not affect the record
        mutableTags.add("c");
        assertEquals(Set.of("a", "b"), skill.tags());

        // returned set must be unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> skill.tags().add("x"));
    }

    @Test
    @DisplayName("Compact constructor defensive-copies metadata via Map.copyOf")
    void compactConstructorDefensiveCopiesMetadata() {
        Map<String, String> mutableMeta = new HashMap<>(Map.of("k", "v"));
        EvolvedSkill skill =
                new EvolvedSkill(
                        "s", null, null, "i", null, null, null, mutableMeta, null, null, 0);

        mutableMeta.put("k2", "v2");
        assertEquals(Map.of("k", "v"), skill.metadata());

        assertThrows(UnsupportedOperationException.class, () -> skill.metadata().put("x", "y"));
    }

    @Test
    @DisplayName(
            "withUpdatedInstructions creates new instance with changed instructions and updated timestamp")
    void withUpdatedInstructions() {
        Instant before = Instant.now();
        EvolvedSkill original =
                new EvolvedSkill(
                        "s",
                        "v1",
                        "desc",
                        "old",
                        "cat",
                        Set.of("t"),
                        SkillTrustLevel.DRAFT,
                        Map.of("k", "v"),
                        before,
                        before,
                        5);

        EvolvedSkill updated = original.withUpdatedInstructions("new");

        assertEquals("new", updated.instructions());
        assertEquals("s", updated.name());
        assertEquals("v1", updated.version());
        assertEquals(5, updated.usageCount());
        assertNotNull(updated.updatedAt());
        // updatedAt should be at or after 'before'
        assertFalse(updated.updatedAt().isBefore(before));
    }

    @Test
    @DisplayName("withUsageIncrement creates new instance with usageCount + 1")
    void withUsageIncrement() {
        Instant before = Instant.now();
        EvolvedSkill original =
                new EvolvedSkill(
                        "s",
                        "v1",
                        "desc",
                        "instr",
                        null,
                        null,
                        SkillTrustLevel.VALIDATED,
                        null,
                        before,
                        before,
                        10);

        EvolvedSkill incremented = original.withUsageIncrement();

        assertEquals(11, incremented.usageCount());
        assertEquals("instr", incremented.instructions());
        assertFalse(incremented.updatedAt().isBefore(before));
    }
}
