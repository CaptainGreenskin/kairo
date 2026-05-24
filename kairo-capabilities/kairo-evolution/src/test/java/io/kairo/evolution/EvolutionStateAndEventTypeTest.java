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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.evolution.event.EvolutionEventType;
import org.junit.jupiter.api.Test;

class EvolutionStateAndEventTypeTest {

    // ---- EvolutionState ----

    @Test
    void exactlySevenStates() {
        assertEquals(7, EvolutionState.values().length);
    }

    @Test
    void allStateNamesMatchConstants() {
        assertEquals(EvolutionState.IDLE, EvolutionState.valueOf("IDLE"));
        assertEquals(EvolutionState.REVIEWING, EvolutionState.valueOf("REVIEWING"));
        assertEquals(EvolutionState.QUARANTINED, EvolutionState.valueOf("QUARANTINED"));
        assertEquals(EvolutionState.APPLIED, EvolutionState.valueOf("APPLIED"));
        assertEquals(EvolutionState.FAILED_RETRYABLE, EvolutionState.valueOf("FAILED_RETRYABLE"));
        assertEquals(EvolutionState.FAILED_HARD, EvolutionState.valueOf("FAILED_HARD"));
        assertEquals(EvolutionState.SUSPENDED, EvolutionState.valueOf("SUSPENDED"));
    }

    @Test
    void stateValueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> EvolutionState.valueOf("UNKNOWN"));
    }

    @Test
    void stateOrdinalsAreDistinctAndContinuous() {
        var values = EvolutionState.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal());
        }
    }

    // ---- EvolutionEventType ----

    @Test
    void exactlyEightEventTypes() {
        assertEquals(8, EvolutionEventType.values().length);
    }

    @Test
    void allEventTypeNamesMatchConstants() {
        assertEquals(EvolutionEventType.SKILL_CREATED, EvolutionEventType.valueOf("SKILL_CREATED"));
        assertEquals(EvolutionEventType.SKILL_UPDATED, EvolutionEventType.valueOf("SKILL_UPDATED"));
        assertEquals(
                EvolutionEventType.SKILL_QUARANTINED,
                EvolutionEventType.valueOf("SKILL_QUARANTINED"));
        assertEquals(
                EvolutionEventType.SKILL_SCAN_PASSED,
                EvolutionEventType.valueOf("SKILL_SCAN_PASSED"));
        assertEquals(
                EvolutionEventType.SKILL_SCAN_REJECTED,
                EvolutionEventType.valueOf("SKILL_SCAN_REJECTED"));
        assertEquals(
                EvolutionEventType.SKILL_ACTIVATED, EvolutionEventType.valueOf("SKILL_ACTIVATED"));
        assertEquals(
                EvolutionEventType.EVOLUTION_SUSPENDED,
                EvolutionEventType.valueOf("EVOLUTION_SUSPENDED"));
        assertEquals(
                EvolutionEventType.EVOLUTION_RESUMED,
                EvolutionEventType.valueOf("EVOLUTION_RESUMED"));
    }

    @Test
    void eventTypeValueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> EvolutionEventType.valueOf("CREATED"));
    }

    @Test
    void eventTypeOrdinalsAreDistinctAndContinuous() {
        var values = EvolutionEventType.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal());
        }
    }
}
