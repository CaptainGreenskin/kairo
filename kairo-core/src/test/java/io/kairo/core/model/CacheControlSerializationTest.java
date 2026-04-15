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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.core.prompt.SystemPromptBuilder;
import io.kairo.core.prompt.SystemPromptResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CacheControlSerializationTest {

    private final ClaudeModelHarness harness = new ClaudeModelHarness();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- Large messages get cache_control metadata ----

    @Test
    void optimizeMessages_largeMessage_getsCacheControlMetadata() {
        Msg largeMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("large content"))
                        .tokenCount(2000) // > 1024 threshold
                        .build();
        Msg reply = Msg.of(MsgRole.ASSISTANT, "response");
        Msg followUp = Msg.of(MsgRole.USER, "more");
        Msg end = Msg.of(MsgRole.ASSISTANT, "done");

        List<Msg> optimized = harness.optimizeMessages(List.of(largeMsg, reply, followUp, end));

        // Large message at index 0 (< size-2) should get cache_control
        assertEquals("ephemeral", optimized.get(0).metadata().get("cache_control"));
    }

    @Test
    void optimizeMessages_smallMessage_noCacheControl() {
        Msg smallMsg = Msg.of(MsgRole.USER, "hello");
        Msg reply = Msg.of(MsgRole.ASSISTANT, "hi");
        Msg followUp = Msg.of(MsgRole.USER, "more");
        Msg end = Msg.of(MsgRole.ASSISTANT, "done");

        List<Msg> optimized = harness.optimizeMessages(List.of(smallMsg, reply, followUp, end));

        // Small messages should not get cache_control
        assertFalse(optimized.get(0).metadata().containsKey("cache_control"));
    }

    @Test
    void optimizeMessages_largeRecentMessage_noCacheControl() {
        // Messages at index >= size-2 should NOT get cache_control (they are recent)
        Msg msg1 = Msg.of(MsgRole.USER, "first");
        Msg msg2 = Msg.of(MsgRole.ASSISTANT, "reply");
        Msg largeRecent =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("recent large"))
                        .tokenCount(5000)
                        .build();

        List<Msg> optimized = harness.optimizeMessages(List.of(msg1, msg2, largeRecent));

        // largeRecent is at index 2 which is >= size(3) - 2 = 1, so no cache_control
        assertFalse(optimized.get(2).metadata().containsKey("cache_control"));
    }

    // ---- cache_control preserved through merging ----

    @Test
    void optimizeMessages_cacheControlPreservedOnMerge() {
        // Two consecutive user messages, the first one large
        Msg largeUser =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("large user message"))
                        .tokenCount(2000)
                        .build();
        Msg smallUser = Msg.of(MsgRole.USER, "follow up");
        Msg reply = Msg.of(MsgRole.ASSISTANT, "ok");
        Msg user3 = Msg.of(MsgRole.USER, "more");
        Msg reply2 = Msg.of(MsgRole.ASSISTANT, "done");

        List<Msg> optimized =
                harness.optimizeMessages(List.of(largeUser, smallUser, reply, user3, reply2));

        // After merging, the merged user msg at index 0 should still have high token count
        // and should get cache_control (index < size-2)
        Msg merged = optimized.get(0);
        assertEquals(MsgRole.USER, merged.role());
        // The merged message should have cache_control since combined tokens > 1024
        assertEquals("ephemeral", merged.metadata().get("cache_control"));
    }

    // ---- SystemPromptResult parts passed through ModelConfig ----

    @Test
    void systemPromptResult_passedThroughModelConfig() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .dynamicBoundary()
                        .section("context", "Working in /tmp")
                        .buildResult();

        Map<String, String> parts = harness.buildSystemPromptParts(result);
        assertNotNull(parts);
        assertEquals(result.staticPrefix(), parts.get("staticPrefix"));
        assertEquals(result.dynamicSuffix(), parts.get("dynamicSuffix"));

        // Build a ModelConfig with these parts
        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .systemPrompt(result.fullPrompt())
                        .systemPromptParts(parts)
                        .build();

        assertNotNull(config.systemPromptParts());
        assertEquals(result.staticPrefix(), config.systemPromptParts().get("staticPrefix"));
        assertEquals(result.dynamicSuffix(), config.systemPromptParts().get("dynamicSuffix"));
    }

    @Test
    void buildSystemPromptParts_noBoundary_returnsNull() {
        SystemPromptResult noBoundary =
                SystemPromptBuilder.create().section("identity", "Bot").buildResult();

        assertNull(harness.buildSystemPromptParts(noBoundary));
    }

    @Test
    void buildSystemPromptParts_nullResult_returnsNull() {
        assertNull(harness.buildSystemPromptParts(null));
    }

    // ---- Static prefix gets cache_control=ephemeral in serialization ----

    @Test
    void anthropicProvider_serializesSystemPromptParts_withCacheControl() throws Exception {
        AnthropicProvider provider = new AnthropicProvider("test-key");

        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .dynamicBoundary()
                        .section("context", "Working in /tmp")
                        .buildResult();

        Map<String, String> parts = new HashMap<>();
        parts.put("staticPrefix", result.staticPrefix());
        parts.put("dynamicSuffix", result.dynamicSuffix());

        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .systemPrompt(result.fullPrompt())
                        .systemPromptParts(parts)
                        .build();

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String body = provider.buildRequestBody(messages, config, false);
        JsonNode root = objectMapper.readTree(body);

        // System should be an array when parts are provided
        assertTrue(root.path("system").isArray());
        JsonNode systemArray = root.path("system");
        assertEquals(2, systemArray.size());

        // First block: static prefix with cache_control
        JsonNode staticBlock = systemArray.get(0);
        assertEquals("text", staticBlock.path("type").asText());
        assertEquals(result.staticPrefix(), staticBlock.path("text").asText());
        assertNotNull(staticBlock.path("cache_control"));
        assertEquals("ephemeral", staticBlock.path("cache_control").path("type").asText());

        // Second block: dynamic suffix without cache_control
        JsonNode dynamicBlock = systemArray.get(1);
        assertEquals("text", dynamicBlock.path("type").asText());
        assertEquals(result.dynamicSuffix(), dynamicBlock.path("text").asText());
        assertTrue(dynamicBlock.path("cache_control").isMissingNode());
    }

    @Test
    void anthropicProvider_serializesPlainSystemPrompt_whenNoParts() throws Exception {
        AnthropicProvider provider = new AnthropicProvider("test-key");

        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .systemPrompt("Plain system prompt")
                        .build();

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String body = provider.buildRequestBody(messages, config, false);
        JsonNode root = objectMapper.readTree(body);

        // System should be a plain string when no parts
        assertTrue(root.path("system").isTextual());
        assertEquals("Plain system prompt", root.path("system").asText());
    }

    // ---- Message-level cache_control serialized correctly ----

    @Test
    void anthropicProvider_serializesMessageCacheControl() throws Exception {
        AnthropicProvider provider = new AnthropicProvider("test-key");

        Msg cachedMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("cached content"))
                        .metadata("cache_control", "ephemeral")
                        .build();

        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .systemPrompt("System")
                        .build();

        String body = provider.buildRequestBody(List.of(cachedMsg), config, false);
        JsonNode root = objectMapper.readTree(body);

        JsonNode msgNode = root.path("messages").get(0);
        // When cache_control is present, content should be serialized as array
        assertTrue(msgNode.path("content").isArray());
        JsonNode contentBlock = msgNode.path("content").get(0);
        assertEquals("ephemeral", contentBlock.path("cache_control").path("type").asText());
    }
}
