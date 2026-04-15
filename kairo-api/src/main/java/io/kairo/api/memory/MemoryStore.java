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
package io.kairo.api.memory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Persistent storage for memory entries. */
public interface MemoryStore {

    /**
     * Save a memory entry. Creates or updates based on the entry ID.
     *
     * @param entry the entry to save
     * @return a Mono emitting the saved entry
     */
    Mono<MemoryEntry> save(MemoryEntry entry);

    /**
     * Get a memory entry by ID.
     *
     * @param id the entry ID
     * @return a Mono emitting the entry, or empty if not found
     */
    Mono<MemoryEntry> get(String id);

    /**
     * Search memory entries by query text within a scope.
     *
     * @param query the search query
     * @param scope the scope to search within
     * @return a Flux of matching entries
     */
    Flux<MemoryEntry> search(String query, MemoryScope scope);

    /**
     * Delete a memory entry by ID.
     *
     * @param id the entry ID
     * @return a Mono completing when deleted
     */
    Mono<Void> delete(String id);

    /**
     * List all memory entries in a given scope.
     *
     * @param scope the scope to list
     * @return a Flux of entries
     */
    Flux<MemoryEntry> list(MemoryScope scope);
}
