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
import java.util.List;
import java.util.UUID;

/**
 * Convenience factory for creating {@link MemoryEntry} instances.
 *
 * <p>All entries default to {@code verbatim=true}, following the "Facts First" principle.
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
                content,
                MemoryScope.SESSION,
                Instant.now(),
                List.of(),
                true);
    }

    /**
     * Create a project-scoped memory entry.
     *
     * @param content the memory content
     * @param tags optional tags for categorization
     * @return a new MemoryEntry scoped to PROJECT
     */
    public static MemoryEntry project(String content, String... tags) {
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                content,
                MemoryScope.PROJECT,
                Instant.now(),
                List.of(tags),
                true);
    }

    /**
     * Create a user-scoped memory entry.
     *
     * @param content the memory content
     * @param tags optional tags for categorization
     * @return a new MemoryEntry scoped to USER
     */
    public static MemoryEntry user(String content, String... tags) {
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                content,
                MemoryScope.USER,
                Instant.now(),
                List.of(tags),
                true);
    }

    /**
     * Create a memory entry with full control over all fields.
     *
     * @param content the memory content
     * @param scope the visibility scope
     * @param tags tags for categorization
     * @param verbatim whether to preserve verbatim (default true)
     * @return a new MemoryEntry
     */
    public static MemoryEntry create(
            String content, MemoryScope scope, List<String> tags, boolean verbatim) {
        return new MemoryEntry(
                UUID.randomUUID().toString(), content, scope, Instant.now(), tags, verbatim);
    }
}
