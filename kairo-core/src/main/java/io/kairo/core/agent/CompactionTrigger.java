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
package io.kairo.core.agent;

import io.kairo.api.context.ContextManager;
import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.message.Msg;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Checks whether the conversation history exceeds the context budget and triggers compaction when
 * needed. After compaction the replacement history is written back via {@link
 * ReActLoop#replaceHistory}.
 *
 * <p>Package-private: not part of the public API.
 */
class CompactionTrigger {

    private static final Logger log = LoggerFactory.getLogger(CompactionTrigger.class);

    private static final Predicate<Msg> DEFAULT_IMPORTANCE_PREDICATE = Msg::verbatimPreserved;

    private final ContextManager contextManager; // nullable
    private final ReActLoop reactLoop;
    private final MemoryStore memoryStore; // nullable
    private final Predicate<Msg> importancePredicate;

    CompactionTrigger(ContextManager contextManager, ReActLoop reactLoop) {
        this(contextManager, reactLoop, null, null);
    }

    CompactionTrigger(
            ContextManager contextManager,
            ReActLoop reactLoop,
            MemoryStore memoryStore,
            Predicate<Msg> importancePredicate) {
        this.contextManager = contextManager;
        this.reactLoop = reactLoop;
        this.memoryStore = memoryStore;
        this.importancePredicate =
                importancePredicate != null ? importancePredicate : DEFAULT_IMPORTANCE_PREDICATE;
    }

    /**
     * Check if compaction is needed and perform it if so. Returns {@code true} wrapped in a Mono if
     * compaction was performed, {@code false} otherwise.
     *
     * @param conversationHistory the current conversation history (read-only view)
     * @return Mono&lt;Boolean&gt; — true if compaction occurred
     */
    Mono<Boolean> checkAndCompact(List<Msg> conversationHistory) {
        if (contextManager == null || !contextManager.needsCompaction(conversationHistory)) {
            return Mono.just(false);
        }

        int previousSize = conversationHistory.size();
        log.info("Context pressure high, triggering compaction...");

        // Flush important messages to memory before compaction (wait for completion)
        return flushImportantMessages(conversationHistory)
                .then(Mono.defer(() -> contextManager
                        .compactMessages(conversationHistory)
                        .map(
                                compacted -> {
                                    if (compacted != null && compacted.size() < previousSize) {
                                        reactLoop.replaceHistory(compacted);
                                        log.info(
                                                "Compaction complete: {} -> {} messages",
                                                previousSize,
                                                reactLoop.getHistory().size());
                                    }
                                    return true;
                                })
                        .defaultIfEmpty(false)));
    }

    /**
     * Flush important messages to memory store before they are lost to compaction. Returns a
     * {@link Mono} that completes when all saves finish. No-op if memoryStore is null.
     */
    private Mono<Void> flushImportantMessages(List<Msg> conversationHistory) {
        if (memoryStore == null) {
            return Mono.empty();
        }

        List<Msg> importantMessages = conversationHistory.stream()
                .filter(importancePredicate)
                .filter(msg -> msg.text() != null && !msg.text().isBlank())
                .toList();

        if (importantMessages.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(importantMessages)
                .flatMap(msg -> {
                    MemoryEntry entry =
                            new MemoryEntry(
                                    UUID.randomUUID().toString(),
                                    msg.text(),
                                    MemoryScope.SESSION,
                                    Instant.now(),
                                    List.of("compaction-flush"),
                                    true);
                    return memoryStore.save(entry)
                            .doOnError(e -> log.warn(
                                    "Failed to flush message to memory store: {}",
                                    msg.text() != null
                                            ? msg.text().substring(0, Math.min(50, msg.text().length()))
                                            : "null",
                                    e))
                            .onErrorComplete();
                }, 10)
                .then()
                .doOnTerminate(() -> log.debug(
                        "Pre-compaction flush complete for {} important messages",
                        importantMessages.size()));
    }
}
