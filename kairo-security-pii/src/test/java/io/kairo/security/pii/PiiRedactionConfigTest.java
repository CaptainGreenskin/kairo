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
        var config = PiiRedactionConfig.defaults();
        assertThat(config.patterns()).hasSize(PiiPattern.values().length);
    }

    @Test
    void defaults_hasPostModelAndPostToolPhases() {
        var config = PiiRedactionConfig.defaults();
        assertThat(config.phases()).contains(GuardrailPhase.POST_MODEL, GuardrailPhase.POST_TOOL);
    }

    @Test
    void defaults_hasOrder100() {
        var config = PiiRedactionConfig.defaults();
        assertThat(config.order()).isEqualTo(100);
    }

    @Test
    void of_returnsConfigWithSelectedPatterns() {
        var config = PiiRedactionConfig.of(PiiPattern.EMAIL, PiiPattern.SSN_US);
        assertThat(config.patterns()).hasSize(2);
    }

    @Test
    void of_singlePattern_returnsConfigWithOnePattern() {
        var config = PiiRedactionConfig.of(PiiPattern.JWT);
        assertThat(config.patterns()).hasSize(1);
        assertThat(config.patterns().values()).contains("<redacted:jwt>");
    }

    @Test
    void withPhases_returnsNewConfigWithUpdatedPhases() {
        var config = PiiRedactionConfig.defaults().withPhases(GuardrailPhase.PRE_MODEL);
        assertThat(config.phases()).containsOnly(GuardrailPhase.PRE_MODEL);
    }

    @Test
    void withOrder_returnsNewConfigWithUpdatedOrder() {
        var config = PiiRedactionConfig.defaults().withOrder(200);
        assertThat(config.order()).isEqualTo(200);
        assertThat(PiiRedactionConfig.defaults().order()).isEqualTo(100);
    }
}
