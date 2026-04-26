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
package io.kairo.core.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SystemPromptResultTest {

    @Test
    void fourArgConstructor_accessors() {
        SystemPromptResult r =
                new SystemPromptResult("static", "dynamic", "static\ndynamic", List.of());
        assertThat(r.staticPrefix()).isEqualTo("static");
        assertThat(r.dynamicSuffix()).isEqualTo("dynamic");
        assertThat(r.fullPrompt()).isEqualTo("static\ndynamic");
        assertThat(r.segments()).isEmpty();
    }

    @Test
    void threeArgConstructor_segmentsDefaultsToEmpty() {
        SystemPromptResult r = new SystemPromptResult("s", "d", "full");
        assertThat(r.segments()).isEmpty();
    }

    @Test
    void hasSegments_falseWhenEmpty() {
        SystemPromptResult r = new SystemPromptResult("s", "d", "full");
        assertThat(r.hasSegments()).isFalse();
    }

    @Test
    void hasSegments_falseWhenNull() {
        SystemPromptResult r = new SystemPromptResult("s", "d", "full", null);
        assertThat(r.hasSegments()).isFalse();
    }

    @Test
    void hasBoundary_trueWhenBothNonEmpty() {
        SystemPromptResult r = new SystemPromptResult("static part", "dynamic part", "full");
        assertThat(r.hasBoundary()).isTrue();
    }

    @Test
    void hasBoundary_falseWhenStaticEmpty() {
        SystemPromptResult r = new SystemPromptResult("", "dynamic", "full");
        assertThat(r.hasBoundary()).isFalse();
    }

    @Test
    void hasBoundary_falseWhenDynamicEmpty() {
        SystemPromptResult r = new SystemPromptResult("static", "", "full");
        assertThat(r.hasBoundary()).isFalse();
    }

    @Test
    void hasBoundary_falseWhenStaticNull() {
        SystemPromptResult r = new SystemPromptResult(null, "dynamic", "full", List.of());
        assertThat(r.hasBoundary()).isFalse();
    }

    @Test
    void equalRecordsAreEqual() {
        SystemPromptResult a = new SystemPromptResult("s", "d", "f");
        SystemPromptResult b = new SystemPromptResult("s", "d", "f");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void hashCodeConsistent() {
        SystemPromptResult r = new SystemPromptResult("s", "d", "f");
        assertThat(r.hashCode()).isEqualTo(r.hashCode());
    }
}
