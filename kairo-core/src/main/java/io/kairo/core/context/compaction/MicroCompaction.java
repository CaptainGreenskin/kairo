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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Level 2 compaction: Clear tool result content, keep structure.
 *
 * <p>Triggers at 85% pressure. Replaces tool result detailed content with a compact summary
 * preserving the tool name, success/failure status, and byte size — but discards the verbose result
 * body.
 */
public class MicroCompaction implements CompactionStrategy {

    private static final Logger log = LoggerFactory.getLogger(MicroCompaction.class);
    private static final float TRIGGER_THRESHOLD = 0.85f;
    private static final int ABSOLUTE_BUFFER = 30_000;
    private static final int PRESERVE_RECENT_MESSAGES = 3;

    @Override
    public boolean shouldTrigger(ContextState state) {
        return HybridThreshold.shouldTrigger(state, TRIGGER_THRESHOLD, ABSOLUTE_BUFFER);
    }

    @Override
    public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config) {
        return Mono.fromCallable(
                () -> {
                    List<Msg> result = new ArrayList<>(messages.size());
                    int tokensSaved = 0;
                    int compactedCount = 0;

                    int recentStart = Math.max(0, messages.size() - PRESERVE_RECENT_MESSAGES);

                    for (int idx = 0; idx < messages.size(); idx++) {
                        Msg msg = messages.get(idx);
                        boolean inRecent = idx >= recentStart;

                        if (hasToolResult(msg)) {
                            List<Content> newContents = new ArrayList<>();
                            int originalTokens = msg.tokenCount();

                            for (Content content : msg.contents()) {
                                if (content instanceof Content.ToolResultContent trc) {
                                    String status = trc.isError() ? "error" : "success";
                                    int byteSize =
                                            trc.content() != null ? trc.content().length() : 0;
                                    String summary =
                                            "[Result: "
                                                    + trc.toolUseId()
                                                    + " - "
                                                    + status
                                                    + " - "
                                                    + byteSize
                                                    + " bytes]";
                                    newContents.add(
                                            new Content.ToolResultContent(
                                                    trc.toolUseId(), summary, trc.isError()));
                                } else {
                                    newContents.add(content);
                                }
                            }

                            Msg compacted =
                                    Msg.builder()
                                            .id(msg.id())
                                            .role(msg.role())
                                            .contents(newContents)
                                            .timestamp(msg.timestamp())
                                            .tokenCount(estimateTokens(newContents))
                                            .verbatimPreserved(false)
                                            .sourceAgentId(msg.sourceAgentId())
                                            .metadata("micro-compacted", true)
                                            .build();
                            result.add(compacted);
                            tokensSaved += originalTokens - compacted.tokenCount();
                            compactedCount++;
                        } else {
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
                            "MicroCompaction: compacted {} tool results, saved ~{} tokens",
                            compactedCount,
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
        return 200;
    }

    @Override
    public String name() {
        return "micro";
    }

    private boolean hasToolResult(Msg msg) {
        return msg.role() == MsgRole.TOOL
                || msg.contents().stream().anyMatch(c -> c instanceof Content.ToolResultContent);
    }

    private int estimateTokens(List<Content> contents) {
        int charCount = 0;
        for (Content c : contents) {
            if (c instanceof Content.TextContent tc) {
                charCount += tc.text().length();
            } else if (c instanceof Content.ToolResultContent trc) {
                charCount += trc.content().length();
            }
        }
        return Math.max(1, charCount / 4);
    }

    private boolean hasThinkingContent(Msg msg) {
        return msg.contents().stream().anyMatch(c -> c instanceof Content.ThinkingContent);
    }

    private Msg replaceThinkingContent(Msg msg) {
        List<Content> newContents = compressThinkingContent(msg.contents());
        return Msg.builder()
                .id(msg.id())
                .role(msg.role())
                .contents(newContents)
                .timestamp(msg.timestamp())
                .tokenCount(Math.max(1, estimateTokens(newContents)))
                .verbatimPreserved(msg.verbatimPreserved())
                .sourceAgentId(msg.sourceAgentId())
                .build();
    }

    private List<Content> compressThinkingContent(List<Content> contents) {
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
