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

import org.junit.jupiter.api.Test;

class EvolutionSignalTest {

    @Test
    void exactlyNineSignals() {
        assertEquals(9, EvolutionSignal.values().length);
    }

    @Test
    void allSignalNamesMatchConstants() {
        assertEquals(EvolutionSignal.START_REVIEW, EvolutionSignal.valueOf("START_REVIEW"));
        assertEquals(EvolutionSignal.REVIEW_COMPLETE, EvolutionSignal.valueOf("REVIEW_COMPLETE"));
        assertEquals(EvolutionSignal.QUARANTINE, EvolutionSignal.valueOf("QUARANTINE"));
        assertEquals(EvolutionSignal.SCAN_PASS, EvolutionSignal.valueOf("SCAN_PASS"));
        assertEquals(EvolutionSignal.SCAN_REJECT, EvolutionSignal.valueOf("SCAN_REJECT"));
        assertEquals(
                EvolutionSignal.FAILURE_RETRYABLE, EvolutionSignal.valueOf("FAILURE_RETRYABLE"));
        assertEquals(EvolutionSignal.FAILURE_HARD, EvolutionSignal.valueOf("FAILURE_HARD"));
        assertEquals(EvolutionSignal.RETRY, EvolutionSignal.valueOf("RETRY"));
        assertEquals(EvolutionSignal.RESUME, EvolutionSignal.valueOf("RESUME"));
    }

    @Test
    void valueOfUnknownSignalThrows() {
        assertThrows(IllegalArgumentException.class, () -> EvolutionSignal.valueOf("UNKNOWN"));
    }

    @Test
    void signalNamesAreDistinct() {
        var values = EvolutionSignal.values();
        var names = java.util.Arrays.stream(values).map(Enum::name).toList();
        assertEquals(names.size(), new java.util.HashSet<>(names).size());
    }
}
