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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelHarness;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.prompt.SystemPromptResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude-specific model harness implementing optimizations for the Anthropic Claude model family.
 *
 * <p>Optimizations include:
 *
 * <ul>
 *   <li>Automatic system message extraction into the API system field
 *   <li>Prompt caching hints via {@code cache_control} markers
 *   <li>Extended thinking auto-enablement for complex tasks
 *   <li>Token budget management
 * </ul>
 */
public class ClaudeModelHarness implements ModelHarness {

    private static final Logger log = LoggerFactory.getLogger(ClaudeModelHarness.class);

    /** Default thinking budget when auto-enabling thinking. */
    private static final int DEFAULT_THINKING_BUDGET = 10000;

    /** Token threshold beyond which we consider enabling thinking automatically. */
    private static final int THINKING_AUTO_ENABLE_THRESHOLD = 500;

    /** Estimated token threshold for auto-applying cache_control to large user messages. */
    private static final int CACHE_CONTROL_TOKEN_THRESHOLD = 1024;

    /** Whether to auto-enable thinking for complex prompts. */
    private final boolean autoThinking;

    /** Create a harness with default settings (auto-thinking enabled). */
    public ClaudeModelHarness() {
        this(true);
    }

    /**
     * Create a harness with configurable auto-thinking.
     *
     * @param autoThinking whether to auto-enable thinking for complex prompts
     */
    public ClaudeModelHarness(boolean autoThinking) {
        this.autoThinking = autoThinking;
    }

    @Override
    public String name() {
        return "claude";
    }

    /**
     * Optimize messages for Claude:
     *
     * <ol>
     *   <li>Extract all SYSTEM messages (they'll be placed in the API system field)
     *   <li>Merge consecutive same-role messages where possible
     *   <li>Add cache_control hints to large context messages
     *   <li>Ensure alternating user/assistant pattern required by Claude
     * </ol>
     */
    @Override
    public List<Msg> optimizeMessages(List<Msg> messages) {
        List<Msg> optimized = new ArrayList<>();

        // Filter out SYSTEM messages (they are extracted into the system parameter)
        List<Msg> nonSystem = messages.stream().filter(m -> m.role() != MsgRole.SYSTEM).toList();

        // Ensure conversation starts with a user message
        // and maintains proper alternation for Claude
        MsgRole lastRole = null;
        for (Msg msg : nonSystem) {
            MsgRole currentRole = msg.role() == MsgRole.TOOL ? MsgRole.USER : msg.role();

            if (lastRole != null && lastRole == currentRole && currentRole == MsgRole.USER) {
                // Merge consecutive user messages
                Msg lastMsg = optimized.get(optimized.size() - 1);
                List<Content> merged = new ArrayList<>(lastMsg.contents());
                merged.addAll(msg.contents());
                Msg mergedMsg =
                        Msg.builder()
                                .id(lastMsg.id())
                                .role(lastMsg.role())
                                .contents(merged)
                                .timestamp(lastMsg.timestamp())
                                .tokenCount(lastMsg.tokenCount() + msg.tokenCount())
                                .verbatimPreserved(
                                        lastMsg.verbatimPreserved() || msg.verbatimPreserved())
                                .sourceAgentId(lastMsg.sourceAgentId())
                                .build();
                optimized.set(optimized.size() - 1, mergedMsg);
            } else {
                optimized.add(msg);
            }
            lastRole = currentRole;
        }

        // Mark large messages with cache hints via metadata
        for (int i = 0; i < optimized.size(); i++) {
            Msg msg = optimized.get(i);
            int estimatedTokens =
                    msg.tokenCount() > 0 ? msg.tokenCount() : MsgBuilder.estimateTokens(msg);
            if (estimatedTokens > CACHE_CONTROL_TOKEN_THRESHOLD && i < optimized.size() - 2) {
                // Add cache hint metadata for large non-recent messages
                // Preserve any existing metadata
                Msg.Builder cacheBuilder =
                        Msg.builder()
                                .id(msg.id())
                                .role(msg.role())
                                .contents(msg.contents())
                                .timestamp(msg.timestamp())
                                .tokenCount(msg.tokenCount())
                                .verbatimPreserved(msg.verbatimPreserved())
                                .sourceAgentId(msg.sourceAgentId());
                // Copy existing metadata and add cache_control
                if (msg.metadata() != null) {
                    for (Map.Entry<String, Object> entry : msg.metadata().entrySet()) {
                        cacheBuilder.metadata(entry.getKey(), entry.getValue());
                    }
                }
                cacheBuilder.metadata("cache_control", "ephemeral");
                optimized.set(i, cacheBuilder.build());
            }
        }

        log.debug(
                "Optimized {} messages to {} messages for Claude",
                messages.size(),
                optimized.size());
        return optimized;
    }

    /**
     * Optimize config for Claude:
     *
     * <ul>
     *   <li>Extract system prompt from messages if not set in config
     *   <li>Auto-enable extended thinking for complex tool-use scenarios
     *   <li>Adjust temperature (must be 1.0 when thinking is enabled)
     * </ul>
     */
    @Override
    public ModelConfig optimizeConfig(ModelConfig config) {
        ModelConfig.Builder builder =
                ModelConfig.builder()
                        .model(config.model())
                        .maxTokens(config.maxTokens())
                        .temperature(config.temperature())
                        .tools(config.tools())
                        .thinking(config.thinking())
                        .systemPrompt(config.systemPrompt())
                        .systemPromptParts(config.systemPromptParts());

        // Auto-enable thinking if conditions are met
        if (autoThinking
                && !config.thinking().enabled()
                && config.tools() != null
                && !config.tools().isEmpty()) {
            log.debug("Auto-enabling extended thinking for Claude with tool use");
            builder.thinking(new ModelConfig.ThinkingConfig(true, DEFAULT_THINKING_BUDGET));
        }

        // When thinking is enabled, temperature must be 1.0 for Claude
        ModelConfig.ThinkingConfig thinking = config.thinking();
        if (thinking.enabled() && config.temperature() != 1.0) {
            log.debug("Adjusting temperature to 1.0 (required when thinking is enabled)");
            builder.temperature(1.0);
        }

        // Ensure maxTokens is sufficient when thinking is enabled
        if (thinking.enabled() && config.maxTokens() < thinking.budgetTokens() + 1000) {
            int adjusted = thinking.budgetTokens() + 4096;
            log.debug(
                    "Increasing maxTokens from {} to {} to accommodate thinking budget",
                    config.maxTokens(),
                    adjusted);
            builder.maxTokens(adjusted);
        }

        return builder.build();
    }

    /**
     * Estimate total token count for a list of messages.
     *
     * @param messages the messages to estimate
     * @return estimated total tokens
     */
    public int estimateTokens(List<Msg> messages) {
        return messages.stream()
                .mapToInt(
                        msg ->
                                msg.tokenCount() > 0
                                        ? msg.tokenCount()
                                        : MsgBuilder.estimateTokens(msg))
                .sum();
    }

    /**
     * Build the system prompt parts map from a {@link SystemPromptResult} for inclusion in {@link
     * ModelConfig}.
     *
     * @param result the system prompt result with static/dynamic separation
     * @return a map suitable for {@link ModelConfig.Builder#systemPromptParts}, or null if no
     *     boundary
     */
    public Map<String, String> buildSystemPromptParts(SystemPromptResult result) {
        if (result == null || !result.hasBoundary()) {
            return null;
        }
        Map<String, String> parts = new HashMap<>();
        parts.put("staticPrefix", result.staticPrefix());
        parts.put("dynamicSuffix", result.dynamicSuffix());
        return parts;
    }

    /**
     * Extract the combined system prompt from SYSTEM messages.
     *
     * @param messages the message list
     * @return the combined system prompt, or null if none
     */
    public String extractSystemPrompt(List<Msg> messages) {
        String combined =
                messages.stream()
                        .filter(m -> m.role() == MsgRole.SYSTEM)
                        .map(Msg::text)
                        .filter(t -> !t.isBlank())
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse(null);
        return combined;
    }
}
