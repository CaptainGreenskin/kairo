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
package io.kairo.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelFallbackManagerTest {

    @Test
    @DisplayName("hasFallback returns true when fallback models are configured")
    void hasFallbackWhenModelsConfigured() {
        var mgr = new ModelFallbackManager(List.of("model-a", "model-b"));
        assertTrue(mgr.hasFallback());
    }

    @Test
    @DisplayName("hasFallback returns false when list is empty")
    void noFallbackWhenEmpty() {
        var mgr = new ModelFallbackManager(List.of());
        assertFalse(mgr.hasFallback());
    }

    @Test
    @DisplayName("hasFallback returns false when constructed with null")
    void noFallbackWhenNull() {
        var mgr = new ModelFallbackManager(null);
        assertFalse(mgr.hasFallback());
    }

    @Test
    @DisplayName("nextFallback returns models in order")
    void nextFallbackReturnsInOrder() {
        var mgr = new ModelFallbackManager(List.of("model-a", "model-b", "model-c"));
        assertEquals("model-a", mgr.nextFallback());
        assertEquals("model-b", mgr.nextFallback());
        assertEquals("model-c", mgr.nextFallback());
    }

    @Test
    @DisplayName("nextFallback returns null when exhausted")
    void nextFallbackReturnsNullWhenExhausted() {
        var mgr = new ModelFallbackManager(List.of("model-a"));
        assertEquals("model-a", mgr.nextFallback());
        assertNull(mgr.nextFallback());
    }

    @Test
    @DisplayName("reset restores all fallbacks")
    void resetRestoresAllFallbacks() {
        var mgr = new ModelFallbackManager(List.of("model-a", "model-b"));
        mgr.nextFallback();
        mgr.nextFallback();
        assertFalse(mgr.hasFallback());

        mgr.reset();
        assertTrue(mgr.hasFallback());
        assertEquals("model-a", mgr.nextFallback());
    }

    @Test
    @DisplayName("currentModel returns primary when no fallback has been used")
    void currentModelReturnsPrimaryWhenNoFallbackUsed() {
        var mgr = new ModelFallbackManager(List.of("model-a"));
        assertEquals("primary-model", mgr.currentModel("primary-model"));
    }

    @Test
    @DisplayName("currentModel returns fallback model after switch")
    void currentModelReturnsFallbackAfterSwitch() {
        var mgr = new ModelFallbackManager(List.of("model-a", "model-b"));
        mgr.nextFallback(); // switches to model-a
        assertEquals("model-a", mgr.currentModel("primary-model"));

        mgr.nextFallback(); // switches to model-b
        assertEquals("model-b", mgr.currentModel("primary-model"));
    }
}
