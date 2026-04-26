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
    void storesStaticPrefix() {
        SystemPromptResult result = new SystemPromptResult("static", "dynamic", "full");
        assertThat(result.staticPrefix()).isEqualTo("static");
    }

    @Test
    void storesDynamicSuffix() {
        SystemPromptResult result = new SystemPromptResult("static", "dynamic", "full");
        assertThat(result.dynamicSuffix()).isEqualTo("dynamic");
    }

    @Test
    void storesFullPrompt() {
        SystemPromptResult result = new SystemPromptResult("static", "dynamic", "static\ndynamic");
        assertThat(result.fullPrompt()).isEqualTo("static\ndynamic");
    }

    @Test
    void backwardCompatConstructorHasEmptySegments() {
        SystemPromptResult result = new SystemPromptResult("a", "b", "ab");
        assertThat(result.segments()).isEmpty();
    }

    @Test
    void hasSegmentsFalseWhenEmpty() {
        SystemPromptResult result = new SystemPromptResult("a", "b", "ab");
        assertThat(result.hasSegments()).isFalse();
    }

    @Test
    void hasBoundaryTrueWhenBothPartsPresent() {
        SystemPromptResult result = new SystemPromptResult("static", "dynamic", "full");
        assertThat(result.hasBoundary()).isTrue();
    }

    @Test
    void hasBoundaryFalseWhenStaticEmpty() {
        SystemPromptResult result = new SystemPromptResult("", "dynamic", "dynamic");
        assertThat(result.hasBoundary()).isFalse();
    }

    @Test
    void hasBoundaryFalseWhenDynamicEmpty() {
        SystemPromptResult result = new SystemPromptResult("static", "", "static");
        assertThat(result.hasBoundary()).isFalse();
    }

    @Test
    void fourArgConstructorStoresSegments() {
        SystemPromptResult result = new SystemPromptResult("a", "b", "ab", List.of());
        assertThat(result.segments()).isEmpty();
    }
}
