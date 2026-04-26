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
package io.kairo.core.memory;

import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.context.compaction.CompactionModelFork;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Manages session-level memory compaction.
 *
 * <p>On session end: summarizes the conversation into 10K-40K token persistent memory. On session
 * start: loads session memory and provides it for system prompt injection.
 */
public class SessionMemoryCompact {

    private static final Logger log = LoggerFactory.getLogger(SessionMemoryCompact.class);

    @SuppressWarnings("unused")
    private static final int MIN_SUMMARY_TOKENS = 10_000;

    @SuppressWarnings("unused")
    private static final int MAX_SUMMARY_TOKENS = 40_000;

    private final MemoryStore memoryStore;
    private final ModelProvider modelProvider;

    /**
     * Create a new SessionMemoryCompact.
     *
     * @param memoryStore the memory store for persistence
     * @param modelProvider the model provider for generating summaries
     */
    public SessionMemoryCompact(MemoryStore memoryStore, ModelProvider modelProvider) {
        this.memoryStore = memoryStore;
        this.modelProvider = modelProvider;
    }

    /**
     * Save session summary to memory store. Uses CompactionModelFork to generate a summary of the
     * conversation history following the 9-dimension summary format.
     *
     * @param sessionId the session identifier
     * @param conversationHistory the conversation messages to summarize
     * @return a Mono emitting the saved MemoryEntry
     */
    public Mono<MemoryEntry> saveSession(String sessionId, List<Msg> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return Mono.empty();
        }

        CompactionModelFork fork = new CompactionModelFork(modelProvider);
        String prompt = buildSessionSummaryPrompt();

        return fork.summarize(conversationHistory, prompt)
                .flatMap(
                        summary -> {
                            MemoryEntry entry =
                                    new MemoryEntry(
                                            "session-" + sessionId,
                                            null,
                                            summary,
                                            null,
                                            MemoryScope.SESSION,
                                            0.7,
                                            null,
                                            Set.of("session", sessionId),
                                            Instant.now(),
                                            null);
                            log.info(
                                    "Saving session memory for session '{}', summary length: {}"
                                            + " chars",
                                    sessionId,
                                    summary.length());
                            return memoryStore.save(entry);
                        })
                .doOnError(e -> log.error("Failed to save session memory: {}", e.getMessage()));
    }

    /**
     * Load session memory for injection into system prompt.
     *
     * @param sessionId the session identifier
     * @return a Mono emitting the session memory content, or empty string if not found
     */
    public Mono<String> loadSession(String sessionId) {
        return memoryStore
                .get("session-" + sessionId)
                .map(MemoryEntry::content)
                .defaultIfEmpty("")
                .doOnNext(
                        content -> {
                            if (!content.isEmpty()) {
                                log.info(
                                        "Loaded session memory for session '{}', length: {} chars",
                                        sessionId,
                                        content.length());
                            }
                        });
    }

    private String buildSessionSummaryPrompt() {
        return """
                Summarize this conversation session for future context recovery.
                The summary should capture:
                1. What task was being worked on
                2. What was accomplished
                3. Key files modified and their states
                4. Important decisions made
                5. Unfinished work and next steps
                6. User preferences observed
                Keep the summary between 10K-40K tokens. Be thorough but concise.""";
    }
}
