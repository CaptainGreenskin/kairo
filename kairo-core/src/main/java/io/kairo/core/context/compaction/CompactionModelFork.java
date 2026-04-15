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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Wraps a {@link ModelProvider} so that summarization calls are isolated from the main
 * conversation.
 *
 * <p>This prevents summarization requests from polluting prompt caches or affecting the main
 * context window. The fork uses cache-safe parameters: no tools, no thinking, minimal system
 * prompt, and a low temperature for deterministic summaries.
 */
public class CompactionModelFork {

    private final ModelProvider delegate;

    /**
     * Create a new fork around the given model provider.
     *
     * @param delegate the underlying model provider to delegate calls to
     */
    public CompactionModelFork(ModelProvider delegate) {
        this.delegate = delegate;
    }

    /**
     * Generate a summary using an isolated model call.
     *
     * <p>Builds a standalone message list with:
     *
     * <ol>
     *   <li>A system message containing only the summary prompt
     *   <li>A user message containing the serialized conversation
     * </ol>
     *
     * <p>The model config uses:
     *
     * <ul>
     *   <li>maxTokens: 20480 (20K)
     *   <li>temperature: 0.3 (deterministic summaries)
     *   <li>No tools (summary doesn't need tools)
     *   <li>No thinking (summary doesn't need chain-of-thought)
     *   <li>No cache_control metadata
     * </ul>
     *
     * @param messages the conversation messages to summarize
     * @param summaryPrompt the prompt instructing how to summarize
     * @return a Mono emitting the summary text
     */
    public Mono<String> summarize(List<Msg> messages, String summaryPrompt) {
        List<Msg> forkMessages = new ArrayList<>();
        forkMessages.add(Msg.of(MsgRole.SYSTEM, summaryPrompt));

        // Serialize conversation to text for summarization
        StringBuilder conversationText = new StringBuilder();
        for (Msg msg : messages) {
            conversationText
                    .append("[")
                    .append(msg.role())
                    .append("]: ")
                    .append(
                            msg.text() != null && !msg.text().isEmpty()
                                    ? msg.text()
                                    : "(non-text content)")
                    .append("\n\n");
        }
        forkMessages.add(Msg.of(MsgRole.USER, conversationText.toString()));

        String model =
                delegate.name().contains("anthropic") ? "claude-sonnet-4-20250514" : "gpt-4o-mini";

        ModelConfig config =
                ModelConfig.builder()
                        .model(model)
                        .maxTokens(20480)
                        .temperature(0.3)
                        .systemPrompt(summaryPrompt)
                        .build();

        return delegate.call(forkMessages, config)
                .map(
                        response ->
                                response.contents().stream()
                                        .filter(Content.TextContent.class::isInstance)
                                        .map(Content.TextContent.class::cast)
                                        .map(Content.TextContent::text)
                                        .collect(Collectors.joining("\n")));
    }
}
