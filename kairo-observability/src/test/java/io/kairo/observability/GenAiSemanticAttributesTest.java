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
package io.kairo.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.common.AttributeKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenAiSemanticAttributesTest {

    // --- All constants non-null ---

    @Test
    void allAttributeKeyConstantsAreNonNull() {
        assertNotNull(GenAiSemanticAttributes.USAGE_INPUT_TOKENS);
        assertNotNull(GenAiSemanticAttributes.USAGE_OUTPUT_TOKENS);
        assertNotNull(GenAiSemanticAttributes.USAGE_CACHE_READ_TOKENS);
        assertNotNull(GenAiSemanticAttributes.USAGE_CACHE_CREATION_TOKENS);
        assertNotNull(GenAiSemanticAttributes.TOOL_NAME);
        assertNotNull(GenAiSemanticAttributes.TOOL_SUCCESS);
        assertNotNull(GenAiSemanticAttributes.TOOL_DURATION_MS);
        assertNotNull(GenAiSemanticAttributes.AGENT_NAME);
        assertNotNull(GenAiSemanticAttributes.AGENT_ITERATION);
        assertNotNull(GenAiSemanticAttributes.MODEL_NAME);
        assertNotNull(GenAiSemanticAttributes.MESSAGE_COUNT);
        assertNotNull(GenAiSemanticAttributes.COMPACTION_STRATEGY);
        assertNotNull(GenAiSemanticAttributes.COMPACTION_TOKENS_SAVED);
        assertNotNull(GenAiSemanticAttributes.CACHE_HIT_RATIO);
        assertNotNull(GenAiSemanticAttributes.CACHE_BROKEN);
        assertNotNull(GenAiSemanticAttributes.CACHE_BREAK_REASONS);
        assertNotNull(GenAiSemanticAttributes.EXCEPTION_TYPE);
        assertNotNull(GenAiSemanticAttributes.EXCEPTION_MESSAGE);
    }

    // --- Key names correct ---

    @Test
    void usageInputTokensKeyNameCorrect() {
        assertEquals("gen_ai.usage.input_tokens", GenAiSemanticAttributes.USAGE_INPUT_TOKENS.getKey());
    }

    @Test
    void usageOutputTokensKeyNameCorrect() {
        assertEquals("gen_ai.usage.output_tokens", GenAiSemanticAttributes.USAGE_OUTPUT_TOKENS.getKey());
    }

    @Test
    void usageCacheReadTokensKeyNameCorrect() {
        assertEquals("gen_ai.usage.cache_read_tokens", GenAiSemanticAttributes.USAGE_CACHE_READ_TOKENS.getKey());
    }

    @Test
    void usageCacheCreationTokensKeyNameCorrect() {
        assertEquals("gen_ai.usage.cache_creation_tokens",
                GenAiSemanticAttributes.USAGE_CACHE_CREATION_TOKENS.getKey());
    }

    @Test
    void toolNameKeyCorrect() {
        assertEquals("gen_ai.tool.name", GenAiSemanticAttributes.TOOL_NAME.getKey());
    }

    @Test
    void toolSuccessKeyCorrect() {
        assertEquals("gen_ai.tool.success", GenAiSemanticAttributes.TOOL_SUCCESS.getKey());
    }

    @Test
    void toolDurationMsKeyCorrect() {
        assertEquals("gen_ai.tool.duration_ms", GenAiSemanticAttributes.TOOL_DURATION_MS.getKey());
    }

    @Test
    void agentNameKeyCorrect() {
        assertEquals("gen_ai.agent.name", GenAiSemanticAttributes.AGENT_NAME.getKey());
    }

    @Test
    void modelNameKeyCorrect() {
        assertEquals("gen_ai.request.model", GenAiSemanticAttributes.MODEL_NAME.getKey());
    }

    @Test
    void messageCountKeyCorrect() {
        assertEquals("gen_ai.request.message_count", GenAiSemanticAttributes.MESSAGE_COUNT.getKey());
    }

    @Test
    void compactionStrategyKeyCorrect() {
        assertEquals("gen_ai.compaction.strategy", GenAiSemanticAttributes.COMPACTION_STRATEGY.getKey());
    }

    @Test
    void compactionTokensSavedKeyCorrect() {
        assertEquals("gen_ai.compaction.tokens_saved", GenAiSemanticAttributes.COMPACTION_TOKENS_SAVED.getKey());
    }

    @Test
    void exceptionTypeKeyCorrect() {
        assertEquals("exception.type", GenAiSemanticAttributes.EXCEPTION_TYPE.getKey());
    }

    @Test
    void exceptionMessageKeyCorrect() {
        assertEquals("exception.message", GenAiSemanticAttributes.EXCEPTION_MESSAGE.getKey());
    }

    // --- kairoKeyToOTel mapping ---

    @Test
    void kairoKeyToOTelMappingComplete() {
        Map<String, AttributeKey<?>> allMappings = GenAiSemanticAttributes.allMappings();
        assertNotNull(allMappings);
        // Should have entries for all Kairo keys
        assertTrue(allMappings.containsKey("token.input"));
        assertTrue(allMappings.containsKey("token.output"));
        assertTrue(allMappings.containsKey("token.cache_read"));
        assertTrue(allMappings.containsKey("token.cache_write"));
        assertTrue(allMappings.containsKey("tool.name"));
        assertTrue(allMappings.containsKey("tool.success"));
        assertTrue(allMappings.containsKey("tool.duration_ms"));
        assertTrue(allMappings.containsKey("exception.type"));
        assertTrue(allMappings.containsKey("exception.message"));
        assertTrue(allMappings.containsKey("compaction.strategy"));
        assertTrue(allMappings.containsKey("compaction.tokens_saved"));
    }

    @Test
    void kairoKeyToOTelReturnsCorrectValues() {
        assertSame(GenAiSemanticAttributes.USAGE_INPUT_TOKENS,
                GenAiSemanticAttributes.kairoKeyToOTel("token.input"));
        assertSame(GenAiSemanticAttributes.USAGE_OUTPUT_TOKENS,
                GenAiSemanticAttributes.kairoKeyToOTel("token.output"));
        assertSame(GenAiSemanticAttributes.USAGE_CACHE_READ_TOKENS,
                GenAiSemanticAttributes.kairoKeyToOTel("token.cache_read"));
        assertSame(GenAiSemanticAttributes.USAGE_CACHE_CREATION_TOKENS,
                GenAiSemanticAttributes.kairoKeyToOTel("token.cache_write"));
        assertSame(GenAiSemanticAttributes.TOOL_NAME,
                GenAiSemanticAttributes.kairoKeyToOTel("tool.name"));
        assertSame(GenAiSemanticAttributes.TOOL_SUCCESS,
                GenAiSemanticAttributes.kairoKeyToOTel("tool.success"));
        assertSame(GenAiSemanticAttributes.TOOL_DURATION_MS,
                GenAiSemanticAttributes.kairoKeyToOTel("tool.duration_ms"));
        assertSame(GenAiSemanticAttributes.EXCEPTION_TYPE,
                GenAiSemanticAttributes.kairoKeyToOTel("exception.type"));
        assertSame(GenAiSemanticAttributes.EXCEPTION_MESSAGE,
                GenAiSemanticAttributes.kairoKeyToOTel("exception.message"));
        assertSame(GenAiSemanticAttributes.COMPACTION_STRATEGY,
                GenAiSemanticAttributes.kairoKeyToOTel("compaction.strategy"));
        assertSame(GenAiSemanticAttributes.COMPACTION_TOKENS_SAVED,
                GenAiSemanticAttributes.kairoKeyToOTel("compaction.tokens_saved"));
    }

    @Test
    void kairoKeyToOTelReturnsNullForUnknownKey() {
        assertNull(GenAiSemanticAttributes.kairoKeyToOTel("unknown.key"));
    }

    @Test
    void allMappingsIsUnmodifiable() {
        Map<String, AttributeKey<?>> mappings = GenAiSemanticAttributes.allMappings();
        assertThrows(UnsupportedOperationException.class,
                () -> mappings.put("new.key", AttributeKey.stringKey("test")));
    }
}
