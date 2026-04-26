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
package io.kairo.core.model.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tracing.Span;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CacheBreakDetectorTest {

    private CacheBreakDetector detector;
    private final String systemPrompt = "You are a helpful assistant.";
    private final List<ToolDefinition> tools =
            List.of(
                    new ToolDefinition(
                            "get_weather",
                            "Get the weather",
                            ToolCategory.GENERAL,
                            new JsonSchema("object", null, null, null),
                            Object.class));

    @BeforeEach
    void setUp() {
        detector = new CacheBreakDetector();
    }

    @Test
    void firstCall_noCacheBroken() {
        var usage = new ModelResponse.Usage(100, 50, 80, 20);
        var result = detector.check(usage, systemPrompt, tools);

        assertFalse(result.isCacheBroken());
        assertTrue(result.reasons().isEmpty());
        assertEquals(80, result.cacheReadTokens());
        assertEquals(20, result.cacheCreationTokens());
        assertEquals(100, result.inputTokens());
    }

    @Test
    void stableCache_noCacheBroken() {
        var usage1 = new ModelResponse.Usage(100, 50, 80, 20);
        detector.check(usage1, systemPrompt, tools);

        // Second call with same or higher cache_read_tokens
        var usage2 = new ModelResponse.Usage(100, 50, 82, 18);
        var result = detector.check(usage2, systemPrompt, tools);

        assertFalse(result.isCacheBroken());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void systemPromptChange_cacheBroken() {
        var usage1 = new ModelResponse.Usage(100, 50, 80, 20);
        detector.check(usage1, systemPrompt, tools);

        // Second call with changed system prompt and dropped cache_read_tokens
        var usage2 = new ModelResponse.Usage(100, 50, 10, 90);
        var result = detector.check(usage2, "You are a different assistant.", tools);

        assertTrue(result.isCacheBroken());
        assertTrue(result.reasons().contains("system_prompt_changed"));
    }

    @Test
    void toolSchemaChange_cacheBroken() {
        var usage1 = new ModelResponse.Usage(100, 50, 80, 20);
        detector.check(usage1, systemPrompt, tools);

        // Second call with changed tools and dropped cache_read_tokens
        List<ToolDefinition> newTools =
                List.of(
                        new ToolDefinition(
                                "search",
                                "Search the web",
                                ToolCategory.GENERAL,
                                new JsonSchema("object", null, null, null),
                                Object.class));
        var usage2 = new ModelResponse.Usage(100, 50, 10, 90);
        var result = detector.check(usage2, systemPrompt, newTools);

        assertTrue(result.isCacheBroken());
        assertTrue(result.reasons().contains("tool_schema_changed"));
    }

    @Test
    void ttlExpiry_cacheBroken() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        detector.setNowOverride(t0);

        var usage1 = new ModelResponse.Usage(100, 50, 80, 20);
        detector.check(usage1, systemPrompt, tools);

        // Advance time by 6 minutes (>5 min TTL)
        Instant t1 = t0.plusSeconds(6 * 60);
        detector.setNowOverride(t1);

        var usage2 = new ModelResponse.Usage(100, 50, 10, 90);
        var result = detector.check(usage2, systemPrompt, tools);

        assertTrue(result.isCacheBroken());
        assertTrue(result.reasons().contains("ttl_expired"));
    }

    @Test
    void multipleReasonsCombined() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        detector.setNowOverride(t0);

        var usage1 = new ModelResponse.Usage(100, 50, 80, 20);
        detector.check(usage1, systemPrompt, tools);

        // Change everything: prompt, tools, and TTL
        Instant t1 = t0.plusSeconds(10 * 60);
        detector.setNowOverride(t1);

        List<ToolDefinition> newTools =
                List.of(
                        new ToolDefinition(
                                "search",
                                "Search the web",
                                ToolCategory.GENERAL,
                                new JsonSchema("object", null, null, null),
                                Object.class));
        var usage2 = new ModelResponse.Usage(100, 50, 5, 95);
        var result = detector.check(usage2, "New system prompt", newTools);

        assertTrue(result.isCacheBroken());
        assertEquals(3, result.reasons().size());
        assertTrue(result.reasons().contains("system_prompt_changed"));
        assertTrue(result.reasons().contains("tool_schema_changed"));
        assertTrue(result.reasons().contains("ttl_expired"));
    }

    @Test
    void hitRatio_calculation() {
        // 80 read, 20 creation → ratio = 80/100 = 0.8
        var result = new CacheCheckResult(80, 20, 100);
        assertEquals(0.8, result.hitRatio(), 0.001);

        // 0 read, 0 creation → ratio = 0.0
        var zeroResult = new CacheCheckResult(0, 0, 100);
        assertEquals(0.0, zeroResult.hitRatio(), 0.001);

        // 100 read, 0 creation → ratio = 1.0
        var fullHit = new CacheCheckResult(100, 0, 100);
        assertEquals(1.0, fullHit.hitRatio(), 0.001);

        // 0 read, 100 creation → ratio = 0.0
        var noHit = new CacheCheckResult(0, 100, 100);
        assertEquals(0.0, noHit.hitRatio(), 0.001);
    }

    @Test
    void spanAttributes_setCorrectly() {
        // Capturing span that records all setAttribute calls
        CapturingSpan span = new CapturingSpan();
        AnthropicProvider provider = new AnthropicProvider("test-key", "https://api.anthropic.com");

        var response =
                new ModelResponse(
                        "resp-1",
                        List.of(),
                        new ModelResponse.Usage(100, 50, 80, 20),
                        ModelResponse.StopReason.END_TURN,
                        "claude-3");

        // First call establishes baseline
        provider.checkCacheAndRecord(response, systemPrompt, tools, span);

        assertEquals(0.8, (double) span.attributes.get("cache.hit_ratio"), 0.001);
        assertEquals(80, span.attributes.get("cache.read_tokens"));
        assertEquals(20, span.attributes.get("cache.creation_tokens"));
        assertNull(span.attributes.get("cache.broken"));

        // Second call with cache break
        span.attributes.clear();
        var brokenResponse =
                new ModelResponse(
                        "resp-2",
                        List.of(),
                        new ModelResponse.Usage(100, 50, 5, 95),
                        ModelResponse.StopReason.END_TURN,
                        "claude-3");
        provider.checkCacheAndRecord(brokenResponse, "changed prompt", tools, span);

        assertTrue((boolean) span.attributes.get("cache.broken"));
        String reasons = (String) span.attributes.get("cache.break_reasons");
        assertTrue(reasons.contains("system_prompt_changed"));
    }

    @Test
    void noPreviousCacheRead_noBrokenFlag() {
        // First call with 0 cache_read_tokens, then second call with 0 → no broken
        var usage1 = new ModelResponse.Usage(100, 50, 0, 100);
        detector.check(usage1, systemPrompt, tools);

        var usage2 = new ModelResponse.Usage(100, 50, 0, 100);
        var result = detector.check(usage2, "different prompt", tools);

        assertFalse(result.isCacheBroken());
    }

    /** A simple Span implementation that captures setAttribute calls for testing. */
    private static class CapturingSpan implements Span {
        final Map<String, Object> attributes = new HashMap<>();

        @Override
        public String spanId() {
            return "test-span";
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Span parent() {
            return null;
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public void setStatus(boolean success, String message) {}

        @Override
        public void end() {}
    }
}
