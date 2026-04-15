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
package io.kairo.api.task;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A unit of work that can be assigned to an agent.
 *
 * <p>Tasks support dependency tracking via {@code blockedBy} and {@code blocks} sets, enabling
 * DAG-based task scheduling.
 */
public class Task {

    private final String id;
    private String subject;
    private String description;
    private TaskStatus status;
    private String owner;
    private final Set<String> blockedBy;
    private final Set<String> blocks;
    private final Map<String, Object> metadata;
    private final Instant createdAt;
    private Instant updatedAt;

    private Task(Builder builder) {
        this.id = builder.id;
        this.subject = Objects.requireNonNull(builder.subject, "subject must not be null");
        this.description = builder.description;
        this.status = builder.status;
        this.owner = builder.owner;
        this.blockedBy = new HashSet<>(builder.blockedBy);
        this.blocks = new HashSet<>(builder.blocks);
        this.metadata = new HashMap<>(builder.metadata);
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public String id() {
        return id;
    }

    public String subject() {
        return subject;
    }

    public String description() {
        return description;
    }

    public TaskStatus status() {
        return status;
    }

    public String owner() {
        return owner;
    }

    public Set<String> blockedBy() {
        return Set.copyOf(blockedBy);
    }

    public Set<String> blocks() {
        return Set.copyOf(blocks);
    }

    public Map<String, Object> metadata() {
        return Map.copyOf(metadata);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setOwner(String owner) {
        this.owner = owner;
        this.updatedAt = Instant.now();
    }

    public void setSubject(String subject) {
        this.subject = subject;
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void addBlockedBy(String taskId) {
        this.blockedBy.add(taskId);
    }

    public void removeBlockedBy(String taskId) {
        this.blockedBy.remove(taskId);
    }

    public void addBlocks(String taskId) {
        this.blocks.add(taskId);
    }

    public void removeBlocks(String taskId) {
        this.blocks.remove(taskId);
    }

    /** Whether this task has no unresolved dependencies. */
    public boolean isUnblocked() {
        return blockedBy.isEmpty();
    }

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link Task}. */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String subject;
        private String description;
        private TaskStatus status = TaskStatus.PENDING;
        private String owner;
        private final Set<String> blockedBy = new HashSet<>();
        private final Set<String> blocks = new HashSet<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder addBlockedBy(String taskId) {
            this.blockedBy.add(taskId);
            return this;
        }

        public Builder addBlocks(String taskId) {
            this.blocks.add(taskId);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Task build() {
            return new Task(this);
        }
    }

    @Override
    public String toString() {
        return "Task{id='" + id + "', subject='" + subject + "', status=" + status + "}";
    }
}
