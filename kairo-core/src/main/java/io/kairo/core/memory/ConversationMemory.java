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
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * User-friendly memory API that delegates to a {@link MemoryStore}.
 *
 * <p>Provides simple remember/recall/forget operations scoped to a specific agent. Keys are stored
 * as tags on memory entries for efficient retrieval.
 */
public class ConversationMemory {

    private final MemoryStore store;
    private final String agentId;

    /**
     * Create a ConversationMemory bound to a specific agent.
     *
     * @param store the backing memory store
     * @param agentId the agent identifier for scoping entries
     */
    public ConversationMemory(MemoryStore store, String agentId) {
        this.store = store;
        this.agentId = agentId;
    }

    /**
     * Store a value with the given key.
     *
     * <p>The key is stored as a tag on the entry for efficient retrieval. The entry is scoped to
     * {@link MemoryScope#AGENT} with a default importance of 0.5.
     *
     * @param key the key (stored as a tag)
     * @param value the content to remember
     * @return a Mono completing when stored
     */
    public Mono<Void> remember(String key, String value) {
        MemoryEntry entry =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        agentId,
                        value,
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(key),
                        Instant.now(),
                        null);
        return store.save(entry).then();
    }

    /**
     * Recall the most recent value stored under the given key.
     *
     * @param key the key to look up (searches by tag)
     * @return a Mono emitting the content, or empty if not found
     */
    public Mono<String> recall(String key) {
        MemoryQuery query =
                MemoryQuery.builder().agentId(agentId).tags(Set.of(key)).limit(1).build();
        return store.search(query).next().map(MemoryEntry::content);
    }

    /**
     * Forget (delete) all entries stored under the given key.
     *
     * @param key the key to forget
     * @return a Mono completing when all matching entries are deleted
     */
    public Mono<Void> forget(String key) {
        MemoryQuery query =
                MemoryQuery.builder().agentId(agentId).tags(Set.of(key)).limit(100).build();
        return store.search(query).flatMap(entry -> store.delete(entry.id())).then();
    }
}
