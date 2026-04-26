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
package io.kairo.security.pii;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.guardrail.GuardrailPhase;
import org.junit.jupiter.api.Test;

class PiiRedactionConfigTest {

    @Test
    void defaultsContainsAllBuiltInPatterns() {
        PiiRedactionConfig config = PiiRedactionConfig.defaults();
        assertThat(config.patterns()).hasSize(PiiPattern.values().length);
    }

    @Test
    void defaultsHasOrder100() {
        assertThat(PiiRedactionConfig.defaults().order()).isEqualTo(100);
    }

    @Test
    void defaultsPhasesContainPostModel() {
        assertThat(PiiRedactionConfig.defaults().phases()).contains(GuardrailPhase.POST_MODEL);
    }

    @Test
    void defaultsPhasesContainPostTool() {
        assertThat(PiiRedactionConfig.defaults().phases()).contains(GuardrailPhase.POST_TOOL);
    }

    @Test
    void ofWithSubsetPatterns() {
        PiiRedactionConfig config = PiiRedactionConfig.of(PiiPattern.EMAIL, PiiPattern.SSN_US);
        assertThat(config.patterns()).hasSize(2);
    }

    @Test
    void withOrderReturnsNewConfig() {
        PiiRedactionConfig original = PiiRedactionConfig.defaults();
        PiiRedactionConfig modified = original.withOrder(200);

        assertThat(modified.order()).isEqualTo(200);
        assertThat(original.order()).isEqualTo(100);
    }

    @Test
    void withPhasesReturnsNewConfig() {
        PiiRedactionConfig original = PiiRedactionConfig.defaults();
        PiiRedactionConfig modified = original.withPhases(GuardrailPhase.PRE_MODEL);

        assertThat(modified.phases()).containsExactly(GuardrailPhase.PRE_MODEL);
        assertThat(original.phases()).contains(GuardrailPhase.POST_MODEL);
    }
}
