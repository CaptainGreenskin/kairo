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
package io.kairo.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompactionPolicyDefaultsTest {

    @Test
    void pressureThresholdMatchesCompactionThresholds() {
        assertThat(CompactionPolicyDefaults.PRESSURE_THRESHOLD)
                .isEqualTo(CompactionThresholds.DEFAULT_TRIGGER_PRESSURE);
    }

    @Test
    void pressureThresholdIsPositive() {
        assertThat(CompactionPolicyDefaults.PRESSURE_THRESHOLD).isGreaterThan(0.0f);
    }

    @Test
    void circuitBreakerThresholdMatchesCompactionThresholds() {
        assertThat(CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_THRESHOLD)
                .isEqualTo(CompactionThresholds.DEFAULT_CB_FAILURE_LIMIT);
    }

    @Test
    void circuitBreakerThresholdIsPositive() {
        assertThat(CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_THRESHOLD).isGreaterThan(0);
    }

    @Test
    void circuitBreakerCooldownMatchesCompactionThresholds() {
        assertThat(CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_COOLDOWN_SECONDS)
                .isEqualTo(CompactionThresholds.DEFAULT_CB_COOLDOWN_SECONDS);
    }

    @Test
    void circuitBreakerCooldownIsPositive() {
        assertThat(CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_COOLDOWN_SECONDS)
                .isGreaterThan(0L);
    }
}
