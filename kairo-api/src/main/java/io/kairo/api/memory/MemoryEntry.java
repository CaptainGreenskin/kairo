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

import io.kairo.api.Stable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A single memory entry stored in the memory system.
 *
 * @param id unique identifier
 * @param agentId the agent that owns this memory (nullable for global entries)
 * @param content summary after compaction
 * @param rawContent nullable — original content before compaction
 * @param scope the visibility scope
 * @param importance importance score between 0.0 and 1.0
 * @param embedding nullable — vector embedding for similarity search
 * @param tags tags for categorization and filtering
 * @param timestamp when this entry was created
 * @param metadata extensible key-value pairs
 */
@Stable(value = "Memory entry record; shape frozen since v0.1", since = "1.0.0")
public record MemoryEntry(
        String id,
        String agentId,
        String content,
        String rawContent,
        MemoryScope scope,
        double importance,
        float[] embedding,
        Set<String> tags,
        Instant timestamp,
        Map<String, Object> metadata) {

    /** Compact constructor — validates importance range and defensively copies mutable fields. */
    public MemoryEntry {
        if (importance < 0.0 || importance > 1.0) {
            throw new IllegalArgumentException(
                    "importance must be between 0.0 and 1.0, was: " + importance);
        }
        embedding = embedding != null ? embedding.clone() : null;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryEntry that)) return false;
        return Double.compare(importance, that.importance) == 0
                && Objects.equals(id, that.id)
                && Objects.equals(agentId, that.agentId)
                && Objects.equals(content, that.content)
                && Objects.equals(rawContent, that.rawContent)
                && scope == that.scope
                && Arrays.equals(embedding, that.embedding)
                && Objects.equals(tags, that.tags)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        int result =
                Objects.hash(
                        id,
                        agentId,
                        content,
                        rawContent,
                        scope,
                        importance,
                        tags,
                        timestamp,
                        metadata);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }

    /**
     * Create a simple session-scoped memory entry with minimal fields.
     *
     * @param id unique identifier
     * @param content the memory content
     * @param tags tags for categorization
     * @return a new MemoryEntry scoped to SESSION with default importance
     */
    public static MemoryEntry session(String id, String content, Set<String> tags) {
        return new MemoryEntry(
                id, null, content, null, MemoryScope.SESSION, 0.5, null, tags, Instant.now(), null);
    }

    /**
     * Create an agent-scoped memory entry.
     *
     * @param id unique identifier
     * @param agentId the owning agent
     * @param content the memory content
     * @param tags tags for categorization
     * @return a new MemoryEntry scoped to AGENT
     */
    public static MemoryEntry agent(String id, String agentId, String content, Set<String> tags) {
        return new MemoryEntry(
                id,
                agentId,
                content,
                null,
                MemoryScope.AGENT,
                0.5,
                null,
                tags,
                Instant.now(),
                null);
    }

    /**
     * Create a global memory entry.
     *
     * @param id unique identifier
     * @param content the memory content
     * @param importance importance score 0.0-1.0
     * @return a new MemoryEntry scoped to GLOBAL
     */
    public static MemoryEntry global(String id, String content, double importance) {
        return new MemoryEntry(
                id,
                null,
                content,
                null,
                MemoryScope.GLOBAL,
                importance,
                null,
                null,
                Instant.now(),
                null);
    }
}
