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

import io.kairo.api.context.CacheScope;
import io.kairo.api.context.SystemPromptSegment;
import io.kairo.api.model.ModelConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicProviderCacheTest {

    @Test
    void multiSegmentPrompt_serializesAsCacheControlledBlocks() {
        var segments =
                List.of(
                        SystemPromptSegment.global("identity", "You are a helpful assistant."),
                        SystemPromptSegment.session("tools", "Available tools: read, write"),
                        SystemPromptSegment.dynamic("context", "Date: 2026-04-15"));

        ModelConfig config =
                ModelConfig.builder().model("claude-sonnet-4-20250514").segments(segments).build();

        assertEquals(3, config.systemPromptSegments().size());
        assertTrue(config.systemPromptSegments().get(0).isCacheable()); // GLOBAL
        assertEquals(CacheScope.GLOBAL, config.systemPromptSegments().get(0).scope());
        assertTrue(config.systemPromptSegments().get(1).isCacheable()); // SESSION
        assertEquals(CacheScope.SESSION, config.systemPromptSegments().get(1).scope());
        assertFalse(config.systemPromptSegments().get(2).isCacheable()); // NONE
        assertEquals(CacheScope.NONE, config.systemPromptSegments().get(2).scope());
    }

    @Test
    void backwardCompat_noSegments_usesLegacyPath() {
        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .systemPrompt("Hello")
                        .systemPromptParts(
                                Map.of("staticPrefix", "static", "dynamicSuffix", "dynamic"))
                        .build();

        assertNull(config.systemPromptSegments());
        assertEquals("Hello", config.systemPrompt());
        assertTrue(config.systemPromptParts().containsKey("staticPrefix"));
        assertTrue(config.systemPromptParts().containsKey("dynamicSuffix"));
    }

    @Test
    void segmentsListIsImmutable() {
        var segments =
                List.of(SystemPromptSegment.global("identity", "You are a helpful assistant."));

        ModelConfig config =
                ModelConfig.builder().model("claude-sonnet-4-20250514").segments(segments).build();

        assertEquals(1, config.systemPromptSegments().size());
        assertThrows(
                UnsupportedOperationException.class, () -> config.systemPromptSegments().add(null));
    }

    @Test
    void addSegment_incrementallyBuildsSegmentList() {
        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .addSegment(SystemPromptSegment.global("identity", "You are helpful."))
                        .addSegment(SystemPromptSegment.session("tools", "read, write"))
                        .build();

        assertEquals(2, config.systemPromptSegments().size());
        assertEquals("identity", config.systemPromptSegments().get(0).name());
        assertEquals("tools", config.systemPromptSegments().get(1).name());
    }

    @Test
    void segmentsNull_whenNotSet() {
        ModelConfig config = ModelConfig.builder().model("claude-sonnet-4-20250514").build();
        assertNull(config.systemPromptSegments());
    }

    @Test
    void segmentsAndLegacyPrompt_canCoexist() {
        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .systemPrompt("Legacy prompt")
                        .addSegment(SystemPromptSegment.global("identity", "New segment"))
                        .build();

        assertEquals("Legacy prompt", config.systemPrompt());
        assertEquals(1, config.systemPromptSegments().size());
    }
}
