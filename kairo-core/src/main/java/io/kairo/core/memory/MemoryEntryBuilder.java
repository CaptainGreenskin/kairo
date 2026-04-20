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
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Convenience factory for creating {@link MemoryEntry} instances.
 *
 * <p>All entries default to importance 0.5 (medium).
 */
public final class MemoryEntryBuilder {

    private MemoryEntryBuilder() {}

    /**
     * Create a session-scoped memory entry.
     *
     * @param content the memory content
     * @return a new MemoryEntry scoped to SESSION
     */
    public static MemoryEntry session(String content) {
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                null,
                content,
                null,
                MemoryScope.SESSION,
                0.5,
                null,
                Set.of(),
                Instant.now(),
                null);
    }

    /**
     * Create an agent-scoped memory entry.
     *
     * @param content the memory content
     * @param tags optional tags for categorization
     * @return a new MemoryEntry scoped to AGENT
     */
    public static MemoryEntry agent(String content, String... tags) {
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                null,
                content,
                null,
                MemoryScope.AGENT,
                0.5,
                null,
                Set.of(tags),
                Instant.now(),
                null);
    }

    /**
     * Create a global memory entry.
     *
     * @param content the memory content
     * @param tags optional tags for categorization
     * @return a new MemoryEntry scoped to GLOBAL
     */
    public static MemoryEntry global(String content, String... tags) {
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                null,
                content,
                null,
                MemoryScope.GLOBAL,
                0.5,
                null,
                Set.of(tags),
                Instant.now(),
                null);
    }

    /**
     * Create a memory entry with full control over all fields.
     *
     * @param content the memory content
     * @param scope the visibility scope
     * @param tags tags for categorization
     * @param importance importance score 0.0-1.0
     * @return a new MemoryEntry
     */
    public static MemoryEntry create(
            String content, MemoryScope scope, Set<String> tags, double importance) {
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                null,
                content,
                null,
                scope,
                importance,
                null,
                tags,
                Instant.now(),
                null);
    }
}
