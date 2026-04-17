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
import io.kairo.api.message.Msg;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Checks whether the conversation history exceeds the context budget and triggers compaction
 * when needed. After compaction the replacement history is written back via
 * {@link ReActLoop#replaceHistory}.
 *
 * <p>Package-private: not part of the public API.
 */
class CompactionTrigger {

    private static final Logger log = LoggerFactory.getLogger(CompactionTrigger.class);

    private final ContextManager contextManager; // nullable
    private final ReActLoop reactLoop;

    CompactionTrigger(ContextManager contextManager, ReActLoop reactLoop) {
        this.contextManager = contextManager;
        this.reactLoop = reactLoop;
    }

    /**
     * Check if compaction is needed and perform it if so. Returns {@code true} wrapped in a Mono
     * if compaction was performed, {@code false} otherwise.
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

        return contextManager
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
                .defaultIfEmpty(false);
    }
}
