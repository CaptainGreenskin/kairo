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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.evolution.event.EvolutionEventType;
import org.junit.jupiter.api.Test;

class EvolutionEnumsTest {

    @Test
    void evolutionSignal_allConstantsPresent() {
        assertThat(EvolutionSignal.values())
                .contains(
                        EvolutionSignal.START_REVIEW,
                        EvolutionSignal.REVIEW_COMPLETE,
                        EvolutionSignal.QUARANTINE,
                        EvolutionSignal.SCAN_PASS,
                        EvolutionSignal.SCAN_REJECT,
                        EvolutionSignal.FAILURE_RETRYABLE,
                        EvolutionSignal.FAILURE_HARD,
                        EvolutionSignal.RETRY,
                        EvolutionSignal.RESUME);
    }

    @Test
    void evolutionSignal_valueOfRoundtrip() {
        assertThat(EvolutionSignal.valueOf("START_REVIEW")).isEqualTo(EvolutionSignal.START_REVIEW);
        assertThat(EvolutionSignal.valueOf("SCAN_PASS")).isEqualTo(EvolutionSignal.SCAN_PASS);
    }

    @Test
    void evolutionState_allConstantsPresent() {
        assertThat(EvolutionState.values())
                .contains(
                        EvolutionState.IDLE,
                        EvolutionState.REVIEWING,
                        EvolutionState.QUARANTINED,
                        EvolutionState.APPLIED,
                        EvolutionState.FAILED_RETRYABLE,
                        EvolutionState.FAILED_HARD,
                        EvolutionState.SUSPENDED);
    }

    @Test
    void evolutionState_valueOfRoundtrip() {
        assertThat(EvolutionState.valueOf("IDLE")).isEqualTo(EvolutionState.IDLE);
        assertThat(EvolutionState.valueOf("SUSPENDED")).isEqualTo(EvolutionState.SUSPENDED);
    }

    @Test
    void evolutionEventType_allConstantsPresent() {
        assertThat(EvolutionEventType.values())
                .contains(
                        EvolutionEventType.SKILL_CREATED,
                        EvolutionEventType.SKILL_UPDATED,
                        EvolutionEventType.SKILL_QUARANTINED,
                        EvolutionEventType.SKILL_SCAN_PASSED,
                        EvolutionEventType.SKILL_SCAN_REJECTED,
                        EvolutionEventType.SKILL_ACTIVATED,
                        EvolutionEventType.EVOLUTION_SUSPENDED,
                        EvolutionEventType.EVOLUTION_RESUMED);
    }

    @Test
    void evolutionEventType_valueOfRoundtrip() {
        assertThat(EvolutionEventType.valueOf("SKILL_CREATED"))
                .isEqualTo(EvolutionEventType.SKILL_CREATED);
    }
}
