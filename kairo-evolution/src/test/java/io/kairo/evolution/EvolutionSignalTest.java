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

class EvolutionSignalTest {

    @Test
    void nineDistinctValues() {
        assertThat(EvolutionSignal.values()).hasSize(9);
    }

    @Test
    void startReviewExists() {
        assertThat(EvolutionSignal.START_REVIEW).isNotNull();
    }

    @Test
    void reviewCompleteExists() {
        assertThat(EvolutionSignal.REVIEW_COMPLETE).isNotNull();
    }

    @Test
    void quarantineExists() {
        assertThat(EvolutionSignal.QUARANTINE).isNotNull();
    }

    @Test
    void scanPassExists() {
        assertThat(EvolutionSignal.SCAN_PASS).isNotNull();
    }

    @Test
    void scanRejectExists() {
        assertThat(EvolutionSignal.SCAN_REJECT).isNotNull();
    }

    @Test
    void failureRetryableExists() {
        assertThat(EvolutionSignal.FAILURE_RETRYABLE).isNotNull();
    }

    @Test
    void failureHardExists() {
        assertThat(EvolutionSignal.FAILURE_HARD).isNotNull();
    }

    @Test
    void retryExists() {
        assertThat(EvolutionSignal.RETRY).isNotNull();
    }

    @Test
    void resumeExists() {
        assertThat(EvolutionSignal.RESUME).isNotNull();
    }

    @Test
    void valuesAreDistinct() {
        EvolutionSignal[] values = EvolutionSignal.values();
        long unique = java.util.Arrays.stream(values).distinct().count();
        assertThat(unique).isEqualTo(values.length);
    }
}
