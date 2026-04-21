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
package io.kairo.core.context.compaction;

import io.kairo.api.context.BoundaryMarker;
import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.CompactionStrategy;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.context.CompactionThresholds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Level 1 compaction: Snip old tool results by age.
 *
 * <p>Triggers at 80% pressure. Finds the oldest tool result messages and replaces their content
 * with a short placeholder, preserving the most recent N tool results intact.
 */
public class SnipCompaction implements CompactionStrategy {

    private static final Logger log = LoggerFactory.getLogger(SnipCompaction.class);
    private static final int ABSOLUTE_BUFFER = 40_000;
    private static final int PRESERVE_RECENT_TOOL_RESULTS = 5;
    private static final int PRESERVE_RECENT_MESSAGES = 3;

    private final float triggerThreshold;

    /** Create with default threshold. */
    public SnipCompaction() {
        this(CompactionThresholds.DEFAULT_SNIP_PRESSURE);
    }

    /**
     * Create with a custom trigger threshold.
     *
     * @param triggerThreshold pressure threshold to trigger this stage
     */
    public SnipCompaction(float triggerThreshold) {
        this.triggerThreshold = triggerThreshold;
    }

    @Override
    public boolean shouldTrigger(ContextState state) {
        return HybridThreshold.shouldTrigger(state, triggerThreshold, ABSOLUTE_BUFFER);
    }

    @Override
    public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config) {
        return Mono.fromCallable(
                () -> {
                    List<Msg> result = new ArrayList<>(messages.size());
                    int tokensSaved = 0;
                    int snippedCount = 0;

                    // Count tool result messages from the end to find which ones to preserve
                    int toolResultCount = 0;
                    for (Msg msg : messages) {
                        if (hasToolResult(msg)) {
                            toolResultCount++;
                        }
                    }

                    int toolResultSeen = 0;
                    int snipThreshold = toolResultCount - PRESERVE_RECENT_TOOL_RESULTS;

                    int recentStart = Math.max(0, messages.size() - PRESERVE_RECENT_MESSAGES);

                    for (int idx = 0; idx < messages.size(); idx++) {
                        Msg msg = messages.get(idx);
                        boolean inRecent = idx >= recentStart;

                        if (hasToolResult(msg) && toolResultSeen < snipThreshold) {
                            toolResultSeen++;
                            // Replace tool result content with a snipped placeholder
                            String toolName = extractToolName(msg);
                            String snippet =
                                    "[Tool result snipped - "
                                            + toolName
                                            + " at "
                                            + msg.timestamp()
                                            + "]";
                            int originalTokens = msg.tokenCount();
                            List<Content> newContents =
                                    compressThinkingContent(
                                            List.of(new Content.TextContent(snippet)), inRecent);
                            Msg snipped =
                                    Msg.builder()
                                            .id(msg.id())
                                            .role(msg.role())
                                            .contents(newContents)
                                            .timestamp(msg.timestamp())
                                            .tokenCount(snippet.length() / 4)
                                            .verbatimPreserved(false)
                                            .sourceAgentId(msg.sourceAgentId())
                                            .metadata("snipped", true)
                                            .build();
                            result.add(snipped);
                            tokensSaved += originalTokens - snipped.tokenCount();
                            snippedCount++;
                        } else {
                            if (hasToolResult(msg)) {
                                toolResultSeen++;
                            }
                            // Process ThinkingContent in non-tool messages
                            if (!inRecent && hasThinkingContent(msg)) {
                                Msg processed = replaceThinkingContent(msg);
                                tokensSaved += msg.tokenCount() - processed.tokenCount();
                                result.add(processed);
                            } else {
                                result.add(msg);
                            }
                        }
                    }

                    log.info(
                            "SnipCompaction: snipped {} tool results, saved ~{} tokens",
                            snippedCount,
                            tokensSaved);

                    BoundaryMarker marker =
                            new BoundaryMarker(
                                    Instant.now(),
                                    name(),
                                    messages.size(),
                                    result.size(),
                                    tokensSaved);
                    return new CompactionResult(result, tokensSaved, marker);
                });
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String name() {
        return "snip";
    }

    private boolean hasToolResult(Msg msg) {
        return msg.role() == MsgRole.TOOL
                || msg.contents().stream().anyMatch(c -> c instanceof Content.ToolResultContent);
    }

    private String extractToolName(Msg msg) {
        return msg.contents().stream()
                .filter(c -> c instanceof Content.ToolResultContent)
                .map(c -> ((Content.ToolResultContent) c).toolUseId())
                .findFirst()
                .orElse("unknown");
    }

    private boolean hasThinkingContent(Msg msg) {
        return msg.contents().stream().anyMatch(c -> c instanceof Content.ThinkingContent);
    }

    private Msg replaceThinkingContent(Msg msg) {
        List<Content> newContents = compressThinkingContent(msg.contents(), false);
        int estimatedTokens = 0;
        for (Content c : newContents) {
            if (c instanceof Content.TextContent tc) {
                estimatedTokens += tc.text().length() / 4;
            } else if (c instanceof Content.ThinkingContent tc) {
                estimatedTokens += tc.thinking().length() / 4;
            } else {
                estimatedTokens += 10;
            }
        }
        return Msg.builder()
                .id(msg.id())
                .role(msg.role())
                .contents(newContents)
                .timestamp(msg.timestamp())
                .tokenCount(Math.max(1, estimatedTokens))
                .verbatimPreserved(msg.verbatimPreserved())
                .sourceAgentId(msg.sourceAgentId())
                .build();
    }

    private List<Content> compressThinkingContent(List<Content> contents, boolean preserve) {
        if (preserve) {
            return contents;
        }
        List<Content> result = new ArrayList<>();
        for (Content c : contents) {
            if (c instanceof Content.ThinkingContent tc) {
                String thinking = tc.thinking();
                int wordCount = thinking.split("\\s+").length;
                String first20 = thinking.substring(0, Math.min(20, thinking.length()));
                result.add(
                        new Content.TextContent(
                                "[Thinking: " + wordCount + " words about " + first20 + "...]"));
            } else {
                result.add(c);
            }
        }
        return result;
    }
}
