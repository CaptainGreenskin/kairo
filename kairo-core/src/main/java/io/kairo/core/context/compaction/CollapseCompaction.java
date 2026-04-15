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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Level 3 compaction: Message group folding.
 *
 * <p>Triggers at 90% pressure. Groups consecutive tool call + result message sequences and
 * collapses them into a single summary message. For example, 5 rounds of file reads become:
 * "[Collapsed: 5 tool calls (read x3, write x2) - all successful]"
 */
public class CollapseCompaction implements CompactionStrategy {

    private static final Logger log = LoggerFactory.getLogger(CollapseCompaction.class);
    private static final float TRIGGER_THRESHOLD = 0.90f;
    private static final int ABSOLUTE_BUFFER = 20_000;
    private static final int MIN_GROUP_SIZE = 3;

    @Override
    public boolean shouldTrigger(ContextState state) {
        return HybridThreshold.shouldTrigger(state, TRIGGER_THRESHOLD, ABSOLUTE_BUFFER);
    }

    @Override
    public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config) {
        return Mono.fromCallable(
                () -> {
                    List<Msg> result = new ArrayList<>();
                    int tokensSaved = 0;
                    int originalCount = messages.size();

                    int i = 0;
                    while (i < messages.size()) {
                        // Detect a run of tool-related messages (ASSISTANT with ToolUse + TOOL
                        // results)
                        if (isToolRelated(messages.get(i))) {
                            int groupStart = i;
                            List<Msg> group = new ArrayList<>();
                            while (i < messages.size() && isToolRelated(messages.get(i))) {
                                group.add(messages.get(i));
                                i++;
                            }

                            if (group.size() >= MIN_GROUP_SIZE) {
                                // Collapse this group
                                int groupTokens = group.stream().mapToInt(Msg::tokenCount).sum();
                                String summary = buildGroupSummary(group);
                                Msg collapsed =
                                        Msg.builder()
                                                .role(MsgRole.ASSISTANT)
                                                .addContent(new Content.TextContent(summary))
                                                .tokenCount(summary.length() / 4)
                                                .metadata("collapsed", true)
                                                .metadata("collapsed-count", group.size())
                                                .build();
                                result.add(collapsed);
                                tokensSaved += groupTokens - collapsed.tokenCount();
                            } else {
                                // Group too small, keep as-is
                                result.addAll(group);
                            }
                        } else {
                            result.add(messages.get(i));
                            i++;
                        }
                    }

                    log.info(
                            "CollapseCompaction: {} -> {} messages, saved ~{} tokens",
                            originalCount,
                            result.size(),
                            tokensSaved);

                    BoundaryMarker marker =
                            new BoundaryMarker(
                                    Instant.now(),
                                    name(),
                                    originalCount,
                                    result.size(),
                                    tokensSaved);
                    return new CompactionResult(result, tokensSaved, marker);
                });
    }

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public String name() {
        return "collapse";
    }

    private boolean isToolRelated(Msg msg) {
        if (msg.role() == MsgRole.TOOL) {
            return true;
        }
        return msg.contents().stream()
                .anyMatch(
                        c ->
                                c instanceof Content.ToolUseContent
                                        || c instanceof Content.ToolResultContent);
    }

    private String buildGroupSummary(List<Msg> group) {
        Map<String, Integer> toolCounts = new HashMap<>();
        int errors = 0;
        int successes = 0;

        for (Msg msg : group) {
            for (Content content : msg.contents()) {
                if (content instanceof Content.ToolUseContent tuc) {
                    toolCounts.merge(tuc.toolName(), 1, Integer::sum);
                }
                if (content instanceof Content.ToolResultContent trc) {
                    if (trc.isError()) {
                        errors++;
                    } else {
                        successes++;
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder("[Collapsed: ");
        sb.append(group.size()).append(" tool calls (");
        List<String> parts = new ArrayList<>();
        toolCounts.forEach((tool, count) -> parts.add(tool + " x" + count));
        sb.append(String.join(", ", parts));
        sb.append(")");
        if (errors == 0) {
            sb.append(" - all successful");
        } else {
            sb.append(" - ")
                    .append(successes)
                    .append(" successful, ")
                    .append(errors)
                    .append(" errors");
        }
        sb.append("]");
        return sb.toString();
    }
}
