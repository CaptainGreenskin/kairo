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
import io.kairo.api.model.ModelProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Level 4 compaction: LLM-generated structured summary.
 *
 * <p>Triggers at 95% pressure. Uses a {@link ModelProvider} to generate a structured summary of the
 * conversation covering 9 dimensions:
 *
 * <ol>
 *   <li>Current task goal
 *   <li>Completed steps
 *   <li>Active file list
 *   <li>Key decisions and reasoning
 *   <li>Problems encountered and solutions
 *   <li>Current progress
 *   <li>Next steps
 *   <li>Important code snippets
 *   <li>User preferences and constraints
 * </ol>
 *
 * <p>If no {@link ModelProvider} is configured, this stage is skipped.
 */
public class AutoCompaction implements CompactionStrategy {

    private static final Logger log = LoggerFactory.getLogger(AutoCompaction.class);
    private static final float TRIGGER_THRESHOLD = 0.95f;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static final String SUMMARIZATION_PROMPT =
            """
            <instructions>
            Summarize this conversation for context continuity. Preserve all information needed to \
            continue the task without loss of context.
            Your summary MUST be under 20,000 tokens. Be concise but preserve all critical information.
            </instructions>

            <analysis_dimensions>
            Analyze the conversation across these 9 dimensions:
            1. Current task goal and user intent
            2. Completed steps and their outcomes
            3. Active files (paths and key content)
            4. Key decisions and reasoning (including thinking process)
            5. Problems encountered and solutions applied
            6. Current progress status
            7. Planned next steps
            8. Important code snippets (verbatim)
            9. User preferences and constraints
            </analysis_dimensions>

            <output_format>
            <summary>
            Provide your structured summary here, organized by the 9 dimensions above.
            Use bullet points for clarity. Include exact file paths and code snippets where relevant.
            </summary>
            </output_format>
            """;

    private final ModelProvider modelProvider;

    /**
     * Create an AutoCompaction with the given model provider.
     *
     * @param modelProvider the model provider for generating summaries (may be null)
     */
    public AutoCompaction(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public boolean shouldTrigger(ContextState state) {
        return state.pressure() >= TRIGGER_THRESHOLD && modelProvider != null;
    }

    @Override
    public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config) {
        if (modelProvider == null) {
            log.warn("AutoCompaction skipped: no ModelProvider configured");
            return Mono.just(
                    new CompactionResult(
                            messages,
                            0,
                            new BoundaryMarker(
                                    Instant.now(), name(), messages.size(), messages.size(), 0)));
        }

        return attemptSummary(messages, config, 0);
    }

    private Mono<CompactionResult> attemptSummary(
            List<Msg> messages, CompactionConfig config, int attempt) {
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            log.warn(
                    "AutoCompaction: all {} retry attempts exhausted, returning original messages",
                    MAX_RETRY_ATTEMPTS);
            return Mono.just(new CompactionResult(messages, 0, null));
        }

        CompactionModelFork fork = new CompactionModelFork(modelProvider);

        return fork.summarize(messages, SUMMARIZATION_PROMPT)
                .map(summary -> buildCompactedResult(messages, summary))
                .onErrorResume(
                        e -> {
                            if (isPromptTooLong(e)) {
                                // Truncate oldest 20% and retry
                                int truncateCount = Math.max(1, messages.size() / 5);
                                List<Msg> truncated =
                                        new ArrayList<>(
                                                messages.subList(truncateCount, messages.size()));
                                log.info(
                                        "AutoCompaction: prompt too long, truncating {} messages"
                                                + " (attempt {}/{})",
                                        truncateCount,
                                        attempt + 1,
                                        MAX_RETRY_ATTEMPTS);
                                return attemptSummary(truncated, config, attempt + 1);
                            }
                            log.error("AutoCompaction failed: {}", e.getMessage());
                            return Mono.just(
                                    new CompactionResult(
                                            messages,
                                            0,
                                            new BoundaryMarker(
                                                    Instant.now(),
                                                    name(),
                                                    messages.size(),
                                                    messages.size(),
                                                    0)));
                        });
    }

    private CompactionResult buildCompactedResult(List<Msg> messages, String summaryText) {
        int originalTokens = messages.stream().mapToInt(Msg::tokenCount).sum();

        // Keep system messages and the last few messages, replace the middle
        List<Msg> result = new ArrayList<>();

        // Preserve system messages
        for (Msg msg : messages) {
            if (msg.role() == MsgRole.SYSTEM) {
                result.add(msg);
            }
        }

        // Add the summary as a system message
        Msg summaryMsg =
                Msg.builder()
                        .role(MsgRole.SYSTEM)
                        .addContent(
                                new Content.TextContent("[Auto-compacted summary]\n" + summaryText))
                        .tokenCount(summaryText.length() / 4)
                        .metadata("auto-compacted", true)
                        .metadata("original-message-count", messages.size())
                        .build();
        result.add(summaryMsg);

        // Preserve the last 3 non-system messages
        List<Msg> nonSystem = messages.stream().filter(m -> m.role() != MsgRole.SYSTEM).toList();
        int keepFrom = Math.max(0, nonSystem.size() - 3);
        for (int i = keepFrom; i < nonSystem.size(); i++) {
            result.add(nonSystem.get(i));
        }

        int newTokens = result.stream().mapToInt(Msg::tokenCount).sum();
        int tokensSaved = originalTokens - newTokens;

        log.info(
                "AutoCompaction: {} -> {} messages, saved ~{} tokens",
                messages.size(),
                result.size(),
                tokensSaved);

        BoundaryMarker marker =
                new BoundaryMarker(
                        Instant.now(), name(), messages.size(), result.size(), tokensSaved);
        return new CompactionResult(result, tokensSaved, marker);
    }

    private boolean isPromptTooLong(Throwable e) {
        String msg = e.getMessage();
        return msg != null
                && (msg.contains("prompt_too_long") || msg.contains("maximum context length"));
    }

    @Override
    public int priority() {
        return 400;
    }

    @Override
    public String name() {
        return "auto";
    }
}
