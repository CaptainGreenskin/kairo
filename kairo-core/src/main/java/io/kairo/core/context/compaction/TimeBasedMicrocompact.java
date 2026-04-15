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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Time-based micro compaction strategy.
 *
 * <p>Triggers when the last user message is older than 60 minutes. Applies micro-compaction style
 * compression to messages older than 30 minutes, while preserving the last 5 tool results
 * regardless of age.
 */
public class TimeBasedMicrocompact implements CompactionStrategy {

    private static final Logger log = LoggerFactory.getLogger(TimeBasedMicrocompact.class);
    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(60);
    private static final Duration AGE_THRESHOLD = Duration.ofMinutes(30);
    private static final int PRESERVE_RECENT_TOOL_RESULTS = 5;

    @Override
    public boolean shouldTrigger(ContextState state) {
        // This strategy is purely time-based; actual time check is done in compact()
        // since ContextState doesn't carry timestamps. We always return true and
        // let compact() decide based on message timestamps.
        return state.messageCount() > 0;
    }

    @Override
    public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config) {
        return Mono.fromCallable(
                () -> {
                    Instant now = Instant.now();

                    // Find the last USER message
                    Instant lastUserTimestamp = null;
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        if (messages.get(i).role() == MsgRole.USER) {
                            lastUserTimestamp = messages.get(i).timestamp();
                            break;
                        }
                    }

                    // If no user message or not idle enough, skip
                    if (lastUserTimestamp == null
                            || Duration.between(lastUserTimestamp, now).compareTo(IDLE_THRESHOLD)
                                    < 0) {
                        return new CompactionResult(
                                messages,
                                0,
                                new BoundaryMarker(
                                        now, name(), messages.size(), messages.size(), 0));
                    }

                    Instant ageThreshold = now.minus(AGE_THRESHOLD);

                    // Count tool result messages to determine which to preserve
                    int toolResultCount = 0;
                    for (Msg msg : messages) {
                        if (hasToolResult(msg)) {
                            toolResultCount++;
                        }
                    }
                    int toolResultSeen = 0;
                    int preserveFrom = toolResultCount - PRESERVE_RECENT_TOOL_RESULTS;

                    List<Msg> result = new ArrayList<>(messages.size());
                    int tokensSaved = 0;
                    int compactedCount = 0;

                    for (Msg msg : messages) {
                        boolean isOld =
                                msg.timestamp() != null && msg.timestamp().isBefore(ageThreshold);
                        boolean isToolResult = hasToolResult(msg);

                        if (isToolResult) {
                            toolResultSeen++;
                        }

                        // Preserve last 5 tool results regardless of age
                        boolean preserveToolResult = isToolResult && toolResultSeen > preserveFrom;

                        if (isOld
                                && !preserveToolResult
                                && !msg.verbatimPreserved()
                                && msg.role() != MsgRole.SYSTEM) {
                            // Apply micro-compaction to old messages
                            if (isToolResult) {
                                int originalTokens = msg.tokenCount();
                                List<Content> newContents = compactToolContents(msg);
                                Msg compacted =
                                        Msg.builder()
                                                .id(msg.id())
                                                .role(msg.role())
                                                .contents(newContents)
                                                .timestamp(msg.timestamp())
                                                .tokenCount(estimateTokens(newContents))
                                                .verbatimPreserved(false)
                                                .sourceAgentId(msg.sourceAgentId())
                                                .metadata("time-micro-compacted", true)
                                                .build();
                                result.add(compacted);
                                tokensSaved += originalTokens - compacted.tokenCount();
                                compactedCount++;
                            } else {
                                // For non-tool old messages, truncate long text content
                                int originalTokens = msg.tokenCount();
                                Msg compacted = truncateMessage(msg);
                                result.add(compacted);
                                tokensSaved += originalTokens - compacted.tokenCount();
                                if (originalTokens > compacted.tokenCount()) {
                                    compactedCount++;
                                }
                            }
                        } else {
                            result.add(msg);
                        }
                    }

                    log.info(
                            "TimeBasedMicrocompact: compacted {} messages, saved ~{} tokens",
                            compactedCount,
                            tokensSaved);

                    BoundaryMarker marker =
                            new BoundaryMarker(
                                    now, name(), messages.size(), result.size(), tokensSaved);
                    return new CompactionResult(result, tokensSaved, marker);
                });
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public String name() {
        return "time-micro";
    }

    private boolean hasToolResult(Msg msg) {
        return msg.role() == MsgRole.TOOL
                || msg.contents().stream().anyMatch(c -> c instanceof Content.ToolResultContent);
    }

    private List<Content> compactToolContents(Msg msg) {
        List<Content> newContents = new ArrayList<>();
        for (Content content : msg.contents()) {
            if (content instanceof Content.ToolResultContent trc) {
                String status = trc.isError() ? "error" : "success";
                int byteSize = trc.content() != null ? trc.content().length() : 0;
                String summary =
                        "[Result: "
                                + trc.toolUseId()
                                + " - "
                                + status
                                + " - "
                                + byteSize
                                + " bytes]";
                newContents.add(
                        new Content.ToolResultContent(trc.toolUseId(), summary, trc.isError()));
            } else {
                newContents.add(content);
            }
        }
        return newContents;
    }

    private Msg truncateMessage(Msg msg) {
        List<Content> newContents = new ArrayList<>();
        for (Content content : msg.contents()) {
            if (content instanceof Content.TextContent tc && tc.text().length() > 500) {
                newContents.add(
                        new Content.TextContent(
                                tc.text().substring(0, 500) + "... [truncated by time-micro]"));
            } else {
                newContents.add(content);
            }
        }
        return Msg.builder()
                .id(msg.id())
                .role(msg.role())
                .contents(newContents)
                .timestamp(msg.timestamp())
                .tokenCount(estimateTokens(newContents))
                .verbatimPreserved(msg.verbatimPreserved())
                .sourceAgentId(msg.sourceAgentId())
                .build();
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
}
