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
import io.kairo.api.context.CompactionConfig.PartialDirection;
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
 * Level 5 compaction: Selective compression (last resort).
 *
 * <p>Triggers at 98% pressure. This is the final safety net. Preserves:
 *
 * <ul>
 *   <li>All system messages
 *   <li>The last N conversation messages
 *   <li>All verbatim-preserved messages
 * </ul>
 *
 * Everything else in the middle is compressed into a brief summary.
 */
public class PartialCompaction implements CompactionStrategy {

    private static final Logger log = LoggerFactory.getLogger(PartialCompaction.class);
    private static final float TRIGGER_THRESHOLD = 0.98f;
    private static final int ABSOLUTE_BUFFER = 5_000;
    private static final int PRESERVE_TAIL_MESSAGES = 5;

    @Override
    public boolean shouldTrigger(ContextState state) {
        return HybridThreshold.shouldTrigger(state, TRIGGER_THRESHOLD, ABSOLUTE_BUFFER);
    }

    @Override
    public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config) {
        if (config.partialDirection() == PartialDirection.UP_TO
                && config.boundaryMarkerId() != null) {
            return compactUpTo(messages, config);
        }
        return compactFrom(messages, config);
    }

    private Mono<CompactionResult> compactFrom(List<Msg> messages, CompactionConfig config) {
        return Mono.fromCallable(
                () -> {
                    List<Msg> result = new ArrayList<>();
                    int originalTokens = messages.stream().mapToInt(Msg::tokenCount).sum();

                    // Partition messages
                    List<Msg> systemMessages = new ArrayList<>();
                    List<Msg> verbatimMessages = new ArrayList<>();
                    List<Msg> otherMessages = new ArrayList<>();

                    for (Msg msg : messages) {
                        if (msg.role() == MsgRole.SYSTEM) {
                            systemMessages.add(msg);
                        } else if (msg.verbatimPreserved()) {
                            verbatimMessages.add(msg);
                        } else {
                            otherMessages.add(msg);
                        }
                    }

                    // Keep system messages
                    result.addAll(systemMessages);

                    // Compress the middle part (non-system, non-verbatim, non-tail)
                    int tailStart = Math.max(0, otherMessages.size() - PRESERVE_TAIL_MESSAGES);
                    List<Msg> middleMessages = otherMessages.subList(0, tailStart);
                    List<Msg> tailMessages = otherMessages.subList(tailStart, otherMessages.size());

                    if (!middleMessages.isEmpty()) {
                        // Create a brief summary of the compressed section
                        int compressedCount = middleMessages.size();
                        int compressedTokens =
                                middleMessages.stream().mapToInt(Msg::tokenCount).sum();
                        String summary =
                                "[Partial compaction: "
                                        + compressedCount
                                        + " messages ("
                                        + compressedTokens
                                        + " tokens) compressed. Keeping system messages, verbatim messages, and last "
                                        + tailMessages.size()
                                        + " messages.]";

                        Msg summaryMsg =
                                Msg.builder()
                                        .role(MsgRole.SYSTEM)
                                        .addContent(new Content.TextContent(summary))
                                        .tokenCount(summary.length() / 4)
                                        .metadata("partial-compacted", true)
                                        .metadata("compressed-count", compressedCount)
                                        .metadata("compressed-tokens", compressedTokens)
                                        .build();
                        result.add(summaryMsg);
                    }

                    // Add verbatim messages (in their original order)
                    result.addAll(verbatimMessages);

                    // Add the tail messages
                    result.addAll(tailMessages);

                    int newTokens = result.stream().mapToInt(Msg::tokenCount).sum();
                    int tokensSaved = originalTokens - newTokens;

                    log.info(
                            "PartialCompaction: {} -> {} messages, saved ~{} tokens",
                            messages.size(),
                            result.size(),
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

    private Mono<CompactionResult> compactUpTo(List<Msg> messages, CompactionConfig config) {
        return Mono.fromCallable(
                () -> {
                    String markerId = config.boundaryMarkerId();
                    int markerIndex = -1;
                    for (int i = 0; i < messages.size(); i++) {
                        if (markerId.equals(messages.get(i).id())) {
                            markerIndex = i;
                            break;
                        }
                    }

                    if (markerIndex < 0) {
                        // Marker not found, fall back to FROM behavior
                        log.warn(
                                "PartialCompaction UP_TO: boundary marker '{}' not found, falling back to FROM",
                                markerId);
                        return compactFrom(messages, config).block();
                    }

                    List<Msg> result = new ArrayList<>();
                    int originalTokens = messages.stream().mapToInt(Msg::tokenCount).sum();

                    // Compress messages from start up to (not including) the marker
                    List<Msg> toCompress = messages.subList(0, markerIndex);
                    List<Msg> toKeep = messages.subList(markerIndex, messages.size());

                    if (!toCompress.isEmpty()) {
                        // Preserve system messages from the compressed section
                        for (Msg msg : toCompress) {
                            if (msg.role() == MsgRole.SYSTEM || msg.verbatimPreserved()) {
                                result.add(msg);
                            }
                        }

                        int compressedCount =
                                (int)
                                        toCompress.stream()
                                                .filter(
                                                        m ->
                                                                m.role() != MsgRole.SYSTEM
                                                                        && !m.verbatimPreserved())
                                                .count();
                        int compressedTokens =
                                toCompress.stream()
                                        .filter(
                                                m ->
                                                        m.role() != MsgRole.SYSTEM
                                                                && !m.verbatimPreserved())
                                        .mapToInt(Msg::tokenCount)
                                        .sum();

                        if (compressedCount > 0) {
                            String summary =
                                    "[Partial compaction (UP_TO): "
                                            + compressedCount
                                            + " messages ("
                                            + compressedTokens
                                            + " tokens) compressed up to marker '"
                                            + markerId
                                            + "'.]";
                            Msg summaryMsg =
                                    Msg.builder()
                                            .role(MsgRole.SYSTEM)
                                            .addContent(new Content.TextContent(summary))
                                            .tokenCount(summary.length() / 4)
                                            .metadata("partial-compacted", true)
                                            .metadata("partial-direction", "UP_TO")
                                            .metadata("compressed-count", compressedCount)
                                            .build();
                            result.add(summaryMsg);
                        }
                    }

                    // Keep everything from the marker onward
                    result.addAll(toKeep);

                    int newTokens = result.stream().mapToInt(Msg::tokenCount).sum();
                    int tokensSaved = originalTokens - newTokens;

                    log.info(
                            "PartialCompaction (UP_TO): {} -> {} messages, saved ~{} tokens",
                            messages.size(),
                            result.size(),
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
        return 500;
    }

    @Override
    public String name() {
        return "partial";
    }
}
