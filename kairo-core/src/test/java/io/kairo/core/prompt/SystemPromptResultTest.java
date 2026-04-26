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

import io.kairo.api.context.SystemPromptSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemPromptResultTest {

    @Test
    void threeArgConstructorDoesNotThrow() {
        assertThat(new SystemPromptResult("static", "dynamic", "full")).isNotNull();
    }

    @Test
    void staticPrefixPreserved() {
        SystemPromptResult r = new SystemPromptResult("static", "dynamic", "full");
        assertThat(r.staticPrefix()).isEqualTo("static");
    }

    @Test
    void dynamicSuffixPreserved() {
        SystemPromptResult r = new SystemPromptResult("static", "dynamic", "full");
        assertThat(r.dynamicSuffix()).isEqualTo("dynamic");
    }

    @Test
    void fullPromptPreserved() {
        SystemPromptResult r = new SystemPromptResult("static", "dynamic", "full");
        assertThat(r.fullPrompt()).isEqualTo("full");
    }

    @Test
    void threeArgConstructorSetsEmptySegments() {
        SystemPromptResult r = new SystemPromptResult("s", "d", "f");
        assertThat(r.segments()).isEmpty();
    }

    @Test
    void hasSegmentsReturnsFalseWhenEmpty() {
        SystemPromptResult r = new SystemPromptResult("s", "d", "f");
        assertThat(r.hasSegments()).isFalse();
    }

    @Test
    void hasSegmentsReturnsTrueWhenPresent() {
        SystemPromptResult r =
                new SystemPromptResult(
                        "s", "d", "f", List.of(SystemPromptSegment.global("sec", "content")));
        assertThat(r.hasSegments()).isTrue();
    }

    @Test
    void hasBoundaryReturnsTrueWhenBothPresent() {
        SystemPromptResult r = new SystemPromptResult("static", "dynamic", "full");
        assertThat(r.hasBoundary()).isTrue();
    }

    @Test
    void hasBoundaryReturnsFalseWhenStaticEmpty() {
        SystemPromptResult r = new SystemPromptResult("", "dynamic", "full");
        assertThat(r.hasBoundary()).isFalse();
    }

    @Test
    void hasBoundaryReturnsFalseWhenDynamicEmpty() {
        SystemPromptResult r = new SystemPromptResult("static", "", "full");
        assertThat(r.hasBoundary()).isFalse();
    }

    @Test
    void equalityViaRecord() {
        SystemPromptResult a = new SystemPromptResult("s", "d", "f");
        SystemPromptResult b = new SystemPromptResult("s", "d", "f");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void inequalityOnDifferentFullPrompt() {
        SystemPromptResult a = new SystemPromptResult("s", "d", "f1");
        SystemPromptResult b = new SystemPromptResult("s", "d", "f2");
        assertThat(a).isNotEqualTo(b);
    }
}
