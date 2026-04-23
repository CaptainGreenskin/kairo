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
package io.kairo.api.execution;

import io.kairo.api.Experimental;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Aggregate root representing a durable execution and its event log.
 *
 * <p>A durable execution captures the full state of an agent's ReAct loop so it can be persisted,
 * recovered, and replayed after a crash.
 *
 * @param executionId unique identifier for this execution
 * @param agentId the agent that owns this execution
 * @param events ordered list of execution events
 * @param checkpoint serialized snapshot for fast recovery (may be null if no checkpoint exists)
 * @param status current status of the execution
 * @param version optimistic lock version — incremented on each status update
 * @param createdAt when the execution was created
 * @param updatedAt when the execution was last modified
 * @since v0.8 (Experimental)
 */
@Experimental("DurableExecution SPI — contract may change in v0.9")
public record DurableExecution(
        String executionId,
        String agentId,
        List<ExecutionEvent> events,
        @Nullable String checkpoint,
        ExecutionStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public DurableExecution {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        events = events != null ? List.copyOf(events) : List.of();
    }
}
