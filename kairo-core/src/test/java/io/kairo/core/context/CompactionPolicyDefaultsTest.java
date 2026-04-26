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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

class CompactionPolicyDefaultsTest {

    @Test
    void pressureThresholdIsPositiveAndBelowOne() {
        assertTrue(CompactionPolicyDefaults.PRESSURE_THRESHOLD > 0f);
        assertTrue(CompactionPolicyDefaults.PRESSURE_THRESHOLD < 1f);
    }

    @Test
    void pressureThresholdIs80Percent() {
        assertEquals(0.80f, CompactionPolicyDefaults.PRESSURE_THRESHOLD, 0.001f);
    }

    @Test
    void circuitBreakerThresholdIsPositive() {
        assertTrue(CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_THRESHOLD > 0);
    }

    @Test
    void circuitBreakerThresholdIsThree() {
        assertEquals(3, CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_THRESHOLD);
    }

    @Test
    void cooldownSecondsIsPositive() {
        assertTrue(CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_COOLDOWN_SECONDS > 0L);
    }

    @Test
    void cooldownSecondsIsThirty() {
        assertEquals(30L, CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_COOLDOWN_SECONDS);
    }

    @Test
    void constructorIsPrivate() throws Exception {
        Constructor<CompactionPolicyDefaults> ctor =
                CompactionPolicyDefaults.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
    }
}
