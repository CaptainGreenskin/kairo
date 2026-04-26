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

import org.junit.jupiter.api.Test;

class EvolutionStateTest {

    @Test
    void sevenDistinctValues() {
        assertThat(EvolutionState.values()).hasSize(7);
    }

    @Test
    void idleExists() {
        assertThat(EvolutionState.IDLE).isNotNull();
    }

    @Test
    void reviewingExists() {
        assertThat(EvolutionState.REVIEWING).isNotNull();
    }

    @Test
    void quarantinedExists() {
        assertThat(EvolutionState.QUARANTINED).isNotNull();
    }

    @Test
    void appliedExists() {
        assertThat(EvolutionState.APPLIED).isNotNull();
    }

    @Test
    void failedRetryableExists() {
        assertThat(EvolutionState.FAILED_RETRYABLE).isNotNull();
    }

    @Test
    void failedHardExists() {
        assertThat(EvolutionState.FAILED_HARD).isNotNull();
    }

    @Test
    void suspendedExists() {
        assertThat(EvolutionState.SUSPENDED).isNotNull();
    }

    @Test
    void valueOfByName() {
        assertThat(EvolutionState.valueOf("IDLE")).isEqualTo(EvolutionState.IDLE);
        assertThat(EvolutionState.valueOf("REVIEWING")).isEqualTo(EvolutionState.REVIEWING);
        assertThat(EvolutionState.valueOf("APPLIED")).isEqualTo(EvolutionState.APPLIED);
    }
}
