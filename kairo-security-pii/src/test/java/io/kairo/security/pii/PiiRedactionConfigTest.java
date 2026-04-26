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
    void defaults_containsAllPiiPatterns() {
        PiiRedactionConfig cfg = PiiRedactionConfig.defaults();
        assertThat(cfg.patterns()).hasSize(PiiPattern.values().length);
    }

    @Test
    void defaults_orderIs100() {
        assertThat(PiiRedactionConfig.defaults().order()).isEqualTo(100);
    }

    @Test
    void defaults_includesPostModelAndPostToolPhases() {
        PiiRedactionConfig cfg = PiiRedactionConfig.defaults();
        assertThat(cfg.phases()).contains(GuardrailPhase.POST_MODEL, GuardrailPhase.POST_TOOL);
    }

    @Test
    void of_subsetOfPatterns() {
        PiiRedactionConfig cfg = PiiRedactionConfig.of(PiiPattern.EMAIL, PiiPattern.SSN_US);
        assertThat(cfg.patterns()).hasSize(2);
    }

    @Test
    void withOrder_returnsNewConfigWithUpdatedOrder() {
        PiiRedactionConfig original = PiiRedactionConfig.defaults();
        PiiRedactionConfig updated = original.withOrder(50);
        assertThat(updated.order()).isEqualTo(50);
        assertThat(original.order()).isEqualTo(100);
    }

    @Test
    void withPhases_returnsNewConfigWithUpdatedPhases() {
        PiiRedactionConfig cfg = PiiRedactionConfig.defaults().withPhases(GuardrailPhase.PRE_TOOL);
        assertThat(cfg.phases()).containsOnly(GuardrailPhase.PRE_TOOL);
    }

    @Test
    void defaults_patternsMapIsNotNull() {
        assertThat(PiiRedactionConfig.defaults().patterns()).isNotNull();
    }

    @Test
    void of_singlePattern_hasOneEntry() {
        PiiRedactionConfig cfg = PiiRedactionConfig.of(PiiPattern.JWT);
        assertThat(cfg.patterns()).hasSize(1);
    }
}
