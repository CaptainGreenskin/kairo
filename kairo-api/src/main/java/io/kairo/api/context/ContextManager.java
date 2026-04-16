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
package io.kairo.api.context;

import io.kairo.api.message.Msg;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Manages the conversation context: messages, token tracking, and compaction.
 *
 * <p>The context manager is the central point for adding messages, tracking token usage, and
 * triggering compaction when the context window is under pressure. It maintains an ordered
 * list of {@link Msg} objects representing the full conversation history visible to the model.
 *
 * <p>When the token count approaches the configured budget (see {@link TokenBudget}),
 * callers should invoke {@link #compact()} to summarize or prune older messages.
 * Individual messages can be exempted from compaction via {@link #markVerbatim(String)},
 * following the "Facts First" principle that preserves critical information.
 *
 * <p><strong>Thread safety:</strong> Implementations must be safe for concurrent reads
 * ({@link #getMessages()}, {@link #getTokenCount()}) but may assume that writes
 * ({@link #addMessage(Msg)}, {@link #compact()}) are externally synchronized.
 *
 * @see TokenBudget
 * @see CompactionResult
 */
public interface ContextManager {

    /**
     * Add a message to the context.
     *
     * @param message the message to add
     */
    void addMessage(Msg message);

    /**
     * Get all messages in the current context.
     *
     * @return the message list
     */
    List<Msg> getMessages();

    /**
     * Get the total token count of the current context.
     *
     * @return the token count
     */
    int getTokenCount();

    /**
     * Get the current token budget status.
     *
     * @return the token budget
     */
    TokenBudget getTokenBudget();

    /**
     * Trigger context compaction to free up tokens.
     *
     * @return a Mono emitting the compaction result
     */
    Mono<CompactionResult> compact();

    /**
     * Mark a message as verbatim (not compressible), following the "Facts First" principle.
     *
     * @param messageId the ID of the message to mark
     */
    void markVerbatim(String messageId);

    /**
     * Check if the given messages need compaction based on token pressure.
     *
     * <p>Default implementation returns {@code false}. Implementations that track token budgets
     * should override.
     *
     * @param msgs the messages to check
     * @return true if compaction should be triggered
     */
    default boolean needsCompaction(List<Msg> msgs) {
        return false;
    }

    /**
     * Run compaction on the provided messages.
     *
     * <p>Default implementation returns the original list unchanged. Implementations with
     * compaction pipelines should override.
     *
     * @param msgs the messages to compact
     * @return a Mono emitting the (possibly compacted) message list
     */
    default Mono<List<Msg>> compactMessages(List<Msg> msgs) {
        return Mono.just(msgs);
    }
}
