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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SystemPromptResultTest {

    @Test
    void fullConstructorFieldAccessors() {
        SystemPromptResult result =
                new SystemPromptResult("static-part", "dynamic-part", "full-prompt", List.of());
        assertEquals("static-part", result.staticPrefix());
        assertEquals("dynamic-part", result.dynamicSuffix());
        assertEquals("full-prompt", result.fullPrompt());
        assertTrue(result.segments().isEmpty());
    }

    @Test
    void backwardCompatConstructorDefaultsEmptySegments() {
        SystemPromptResult result = new SystemPromptResult("s", "d", "full");
        assertNotNull(result.segments());
        assertTrue(result.segments().isEmpty());
    }

    @Test
    void hasSegmentsReturnsFalseForEmptyList() {
        SystemPromptResult result = new SystemPromptResult("s", "d", "full", List.of());
        assertFalse(result.hasSegments());
    }

    @Test
    void hasSegmentsReturnsFalseForDefaultConstructor() {
        SystemPromptResult result = new SystemPromptResult("s", "d", "full");
        assertFalse(result.hasSegments());
    }

    @Test
    void hasBoundaryReturnsTrueWhenBothPartsNonEmpty() {
        SystemPromptResult result = new SystemPromptResult("static", "dynamic", "full");
        assertTrue(result.hasBoundary());
    }

    @Test
    void hasBoundaryReturnsFalseWhenStaticPrefixEmpty() {
        SystemPromptResult result = new SystemPromptResult("", "dynamic", "full");
        assertFalse(result.hasBoundary());
    }

    @Test
    void hasBoundaryReturnsFalseWhenDynamicSuffixEmpty() {
        SystemPromptResult result = new SystemPromptResult("static", "", "full");
        assertFalse(result.hasBoundary());
    }

    @Test
    void hasBoundaryReturnsFalseWhenStaticPrefixNull() {
        SystemPromptResult result = new SystemPromptResult(null, "dynamic", "full");
        assertFalse(result.hasBoundary());
    }

    @Test
    void hasBoundaryReturnsFalseWhenDynamicSuffixNull() {
        SystemPromptResult result = new SystemPromptResult("static", null, "full");
        assertFalse(result.hasBoundary());
    }

    @Test
    void recordEquality() {
        SystemPromptResult a = new SystemPromptResult("s", "d", "f");
        SystemPromptResult b = new SystemPromptResult("s", "d", "f");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityOnDifferentFullPrompt() {
        SystemPromptResult a = new SystemPromptResult("s", "d", "prompt-a");
        SystemPromptResult b = new SystemPromptResult("s", "d", "prompt-b");
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsStaticPrefix() {
        SystemPromptResult result = new SystemPromptResult("my-static", "d", "f");
        assertTrue(result.toString().contains("my-static"));
    }
}
