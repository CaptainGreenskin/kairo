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
package io.kairo.core.task;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Immutable representation of a single task with dependency tracking.
 *
 * @param id auto-generated unique identifier
 * @param subject brief, actionable title
 * @param description detailed description of what needs to be done
 * @param status current lifecycle status
 * @param owner agent name that owns this task, or null
 * @param blocks task IDs that THIS task blocks (downstream dependents)
 * @param blockedBy task IDs that block THIS task (upstream dependencies)
 * @param activeForm present continuous form shown in spinner when in_progress
 * @param metadata arbitrary key-value metadata
 */
public record TaskEntry(
        String id,
        String subject,
        @Nullable String description,
        TaskStatus status,
        @Nullable String owner,
        Set<String> blocks,
        Set<String> blockedBy,
        @Nullable String activeForm,
        Map<String, Object> metadata) {

    public TaskEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        status = status == null ? TaskStatus.PENDING : status;
        blocks = blocks == null ? Set.of() : Set.copyOf(blocks);
        blockedBy = blockedBy == null ? Set.of() : Set.copyOf(blockedBy);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Returns blockedBy IDs filtered to only non-completed tasks. A blocker that has been completed
     * is considered resolved and excluded.
     */
    public Set<String> unresolvedBlockers(Map<String, TaskEntry> allTasks) {
        Set<String> unresolved = new HashSet<>();
        for (String blockerId : blockedBy) {
            TaskEntry blocker = allTasks.get(blockerId);
            if (blocker != null && blocker.status() != TaskStatus.COMPLETED) {
                unresolved.add(blockerId);
            }
        }
        return unresolved;
    }

    public TaskEntry withStatus(TaskStatus newStatus) {
        return new TaskEntry(
                id,
                subject,
                description,
                newStatus,
                owner,
                blocks,
                blockedBy,
                activeForm,
                metadata);
    }

    public TaskEntry withOwner(@Nullable String newOwner) {
        return new TaskEntry(
                id,
                subject,
                description,
                status,
                newOwner,
                blocks,
                blockedBy,
                activeForm,
                metadata);
    }

    public TaskEntry withSubject(String newSubject) {
        return new TaskEntry(
                id,
                newSubject,
                description,
                status,
                owner,
                blocks,
                blockedBy,
                activeForm,
                metadata);
    }

    public TaskEntry withDescription(@Nullable String newDescription) {
        return new TaskEntry(
                id,
                subject,
                newDescription,
                status,
                owner,
                blocks,
                blockedBy,
                activeForm,
                metadata);
    }

    public TaskEntry withActiveForm(@Nullable String newActiveForm) {
        return new TaskEntry(
                id,
                subject,
                description,
                status,
                owner,
                blocks,
                blockedBy,
                newActiveForm,
                metadata);
    }

    public TaskEntry withBlocks(Set<String> newBlocks) {
        return new TaskEntry(
                id,
                subject,
                description,
                status,
                owner,
                newBlocks,
                blockedBy,
                activeForm,
                metadata);
    }

    public TaskEntry withBlockedBy(Set<String> newBlockedBy) {
        return new TaskEntry(
                id,
                subject,
                description,
                status,
                owner,
                blocks,
                newBlockedBy,
                activeForm,
                metadata);
    }

    /**
     * Merge metadata patch into existing metadata. Keys with null values are removed; others are
     * added or overwritten.
     */
    public TaskEntry withMergedMetadata(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(this.metadata);
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            if (e.getValue() == null) {
                merged.remove(e.getKey());
            } else {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return new TaskEntry(
                id, subject, description, status, owner, blocks, blockedBy, activeForm, merged);
    }
}
