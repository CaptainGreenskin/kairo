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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaudeModelHarnessTest {

    private final ClaudeModelHarness harness = new ClaudeModelHarness();

    @Test
    void name() {
        assertEquals("claude", harness.name());
    }

    @Test
    void extractSystemPromptFromSingleMessage() {
        List<Msg> messages =
                List.of(Msg.of(MsgRole.SYSTEM, "You are a coder"), Msg.of(MsgRole.USER, "Hi"));
        String system = harness.extractSystemPrompt(messages);
        assertEquals("You are a coder", system);
    }

    @Test
    void extractSystemPromptCombinesMultiple() {
        List<Msg> messages =
                List.of(
                        Msg.of(MsgRole.SYSTEM, "Part one"),
                        Msg.of(MsgRole.SYSTEM, "Part two"),
                        Msg.of(MsgRole.USER, "Hi"));
        String system = harness.extractSystemPrompt(messages);
        assertTrue(system.contains("Part one"));
        assertTrue(system.contains("Part two"));
    }

    @Test
    void extractSystemPromptReturnsNullWhenNone() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hi"));
        assertNull(harness.extractSystemPrompt(messages));
    }

    @Test
    void optimizeMessagesFiltersOutSystem() {
        List<Msg> messages =
                List.of(
                        Msg.of(MsgRole.SYSTEM, "system prompt"),
                        Msg.of(MsgRole.USER, "Hello"),
                        Msg.of(MsgRole.ASSISTANT, "Hi"));
        List<Msg> optimized = harness.optimizeMessages(messages);
        assertTrue(optimized.stream().noneMatch(m -> m.role() == MsgRole.SYSTEM));
        assertEquals(2, optimized.size());
    }

    @Test
    void optimizeMessagesMergesConsecutiveUserMessages() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "First"), Msg.of(MsgRole.USER, "Second"));
        List<Msg> optimized = harness.optimizeMessages(messages);
        assertEquals(1, optimized.size());
        assertEquals(MsgRole.USER, optimized.get(0).role());
        assertEquals(2, optimized.get(0).contents().size());
    }

    @Test
    void optimizeMessagesDoesNotMergeNonConsecutiveSameRole() {
        List<Msg> messages =
                List.of(
                        Msg.of(MsgRole.USER, "First"),
                        Msg.of(MsgRole.ASSISTANT, "Reply"),
                        Msg.of(MsgRole.USER, "Second"));
        List<Msg> optimized = harness.optimizeMessages(messages);
        assertEquals(3, optimized.size());
    }

    @Test
    void optimizeMessagesTreatsToolAsUser() {
        // TOOL messages are treated as USER for alternation check
        Msg toolMsg =
                Msg.builder()
                        .role(MsgRole.TOOL)
                        .addContent(new Content.ToolResultContent("id", "result", false))
                        .build();
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "do something"), toolMsg);
        List<Msg> optimized = harness.optimizeMessages(messages);
        // Both treated as USER, should be merged
        assertEquals(1, optimized.size());
    }

    @Test
    void optimizeConfigEnforcesTemperature1WhenThinkingEnabled() {
        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .temperature(0.5)
                        .thinking(new ModelConfig.ThinkingConfig(true, 10000))
                        .build();
        ModelConfig optimized = harness.optimizeConfig(config);
        assertEquals(1.0, optimized.temperature(), 0.001);
    }

    @Test
    void optimizeConfigKeepsTemperatureWhenThinkingDisabled() {
        ModelConfig config =
                ModelConfig.builder().model("claude-sonnet-4-20250514").temperature(0.7).build();
        // No tools, so auto-thinking won't kick in with autoThinking=true but no tools
        ClaudeModelHarness h = new ClaudeModelHarness(true);
        ModelConfig optimized = h.optimizeConfig(config);
        assertEquals(0.7, optimized.temperature(), 0.001);
    }

    @Test
    void optimizeConfigAutoEnablesThinkingWithTools() {
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "run commands",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        ModelConfig config =
                ModelConfig.builder().model("claude-sonnet-4-20250514").addTool(tool).build();
        ClaudeModelHarness h = new ClaudeModelHarness(true);
        ModelConfig optimized = h.optimizeConfig(config);
        assertTrue(optimized.thinking().enabled());
    }

    @Test
    void optimizeConfigDoesNotAutoEnableWhenDisabled() {
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "run commands",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        ModelConfig config =
                ModelConfig.builder().model("claude-sonnet-4-20250514").addTool(tool).build();
        ClaudeModelHarness h = new ClaudeModelHarness(false);
        ModelConfig optimized = h.optimizeConfig(config);
        assertFalse(optimized.thinking().enabled());
    }

    @Test
    void optimizeConfigIncreasesMaxTokensForThinking() {
        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .maxTokens(2000)
                        .thinking(new ModelConfig.ThinkingConfig(true, 10000))
                        .build();
        ModelConfig optimized = harness.optimizeConfig(config);
        assertTrue(optimized.maxTokens() >= 10000 + 1000);
    }

    @Test
    void cacheHintAddedToLargeMessages() {
        // Create a message with large token count
        Msg largeMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("large content"))
                        .tokenCount(2000)
                        .build();
        Msg smallMsg1 = Msg.of(MsgRole.ASSISTANT, "reply");
        Msg smallMsg2 = Msg.of(MsgRole.USER, "follow up");
        Msg smallMsg3 = Msg.of(MsgRole.ASSISTANT, "end");

        List<Msg> messages = List.of(largeMsg, smallMsg1, smallMsg2, smallMsg3);
        List<Msg> optimized = harness.optimizeMessages(messages);

        // The large message at index 0 should get cache_control metadata
        // (it's at index < size-2 and tokenCount > 1024)
        assertEquals("ephemeral", optimized.get(0).metadata().get("cache_control"));
    }

    @Test
    void estimateTokensUsesStoredTokenCount() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("test"))
                        .tokenCount(42)
                        .build();
        assertEquals(42, harness.estimateTokens(List.of(msg)));
    }
}
