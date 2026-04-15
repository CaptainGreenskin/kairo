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

import io.kairo.api.model.IntRange;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelCapabilityRegistryTest {

    @Test
    @DisplayName("Exact match lookup for claude-3-5-sonnet")
    void lookupExactMatch() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("claude-3-5-sonnet");
        assertEquals("claude", cap.modelFamily());
        assertEquals("sonnet", cap.modelTier());
        assertTrue(cap.supportsThinking());
    }

    @Test
    @DisplayName("Prefix match: claude-3-5-sonnet-20241022 matches claude-3-5-sonnet")
    void lookupPrefixMatch() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("claude-3-5-sonnet-20241022");
        assertEquals("claude", cap.modelFamily());
        assertEquals("sonnet", cap.modelTier());
    }

    @Test
    @DisplayName("Family fallback for unknown claude model")
    void lookupFamilyFallback_claude() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("claude-future-model");
        assertEquals("claude", cap.modelFamily());
        assertEquals("sonnet", cap.modelTier()); // default Claude falls back to sonnet
    }

    @Test
    @DisplayName("Family fallback for unknown gpt model")
    void lookupFamilyFallback_gpt() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("gpt-5-future");
        assertEquals("gpt", cap.modelFamily());
        assertEquals("4o", cap.modelTier());
    }

    @Test
    @DisplayName("Family fallback for unknown glm model")
    void lookupFamilyFallback_glm() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("glm-5-future");
        assertEquals("glm", cap.modelFamily());
    }

    @Test
    @DisplayName("Unknown model returns universal default")
    void lookupUnknownModel_returnsDefault() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("totally-unknown-model");
        assertEquals("unknown", cap.modelFamily());
        assertEquals("default", cap.modelTier());
    }

    @Test
    @DisplayName("Custom registration overrides defaults")
    void customRegistration() {
        ModelCapability custom =
                new ModelCapability(
                        "custom", "v1", 64_000, 4096, false, false, ToolVerbosity.STANDARD, null);
        ModelCapabilityRegistry.register("custom-model-v1", custom);

        ModelCapability cap = ModelCapabilityRegistry.lookup("custom-model-v1");
        assertEquals("custom", cap.modelFamily());
        assertEquals("v1", cap.modelTier());
        assertEquals(64_000, cap.contextWindow());
    }

    @Test
    @DisplayName("Haiku model uses concise verbosity")
    void haikuModel_conciseVerbosity() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("claude-3-5-haiku");
        assertEquals(ToolVerbosity.CONCISE, cap.toolVerbosity());
    }

    @Test
    @DisplayName("Opus model uses verbose verbosity")
    void opusModel_verboseVerbosity() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("claude-3-opus");
        assertEquals(ToolVerbosity.VERBOSE, cap.toolVerbosity());
    }

    @Test
    @DisplayName("Sonnet model supports thinking")
    void sonnetModel_supportsThinking() {
        ModelCapability cap = ModelCapabilityRegistry.lookup("claude-sonnet-4");
        assertTrue(cap.supportsThinking());
        assertNotNull(cap.thinkingBudgetRange());
        assertEquals(new IntRange(2048, 16384), cap.thinkingBudgetRange());
    }

    @Test
    @DisplayName("Null modelId returns universal default")
    void lookupNullModel() {
        ModelCapability cap = ModelCapabilityRegistry.lookup(null);
        assertEquals("unknown", cap.modelFamily());
    }
}
