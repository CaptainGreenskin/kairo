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
package io.kairo.core.agent.snapshot;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.SnapshotStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Manages named checkpoints (savepoints) for agent state, enabling rollback to a previous point.
 *
 * <p>Uses an underlying {@link SnapshotStore} for persistence. Checkpoint IDs are prefixed with
 * {@code "checkpoint:"} to namespace them from regular snapshots.
 *
 * <p>This is an in-process checkpoint mechanism suitable for single-JVM deployments. Cross-process
 * durable execution is deferred to v0.7.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * CheckpointManager mgr = new CheckpointManager(new InMemorySnapshotStore());
 * mgr.savepoint("before-tool-call", agent).block();
 * // ... agent processes ...
 * AgentSnapshot snap = mgr.rollback("before-tool-call").block();
 * Agent restored = AgentBuilder.create("agent").restoreFrom(snap).build();
 * }</pre>
 */
public class CheckpointManager {

    private static final String KEY_PREFIX = "checkpoint:";

    private final SnapshotStore snapshotStore;

    /**
     * Create a CheckpointManager backed by the given store.
     *
     * @param snapshotStore the underlying snapshot storage backend
     */
    public CheckpointManager(SnapshotStore snapshotStore) {
        if (snapshotStore == null) {
            throw new IllegalArgumentException("snapshotStore must not be null");
        }
        this.snapshotStore = snapshotStore;
    }

    /**
     * Create a named savepoint for an agent, capturing its current state.
     *
     * <p>If a checkpoint with the same ID already exists, it will be overwritten.
     *
     * @param checkpointId the unique identifier for this checkpoint
     * @param agent the agent whose state to capture
     * @return a Mono completing when the savepoint is persisted
     * @throws IllegalArgumentException if checkpointId or agent is null
     */
    public Mono<Void> savepoint(String checkpointId, Agent agent) {
        if (checkpointId == null || checkpointId.isBlank()) {
            return Mono.error(
                    new IllegalArgumentException("checkpointId must not be null or blank"));
        }
        if (agent == null) {
            return Mono.error(new IllegalArgumentException("agent must not be null"));
        }
        AgentSnapshot snapshot = agent.snapshot();
        return snapshotStore.save(KEY_PREFIX + checkpointId, snapshot);
    }

    /**
     * Rollback to a previously saved checkpoint.
     *
     * <p>Returns the stored {@link AgentSnapshot}. The caller is responsible for rebuilding the
     * agent via {@code AgentBuilder.restoreFrom(snapshot)}.
     *
     * @param checkpointId the checkpoint to roll back to
     * @return a Mono emitting the snapshot, or empty if the checkpoint does not exist
     */
    public Mono<AgentSnapshot> rollback(String checkpointId) {
        if (checkpointId == null || checkpointId.isBlank()) {
            return Mono.error(
                    new IllegalArgumentException("checkpointId must not be null or blank"));
        }
        return snapshotStore.load(KEY_PREFIX + checkpointId);
    }

    /**
     * List all checkpoint IDs managed by this instance.
     *
     * @return a Flux of checkpoint IDs (without the internal prefix)
     */
    public Flux<String> listCheckpoints() {
        return snapshotStore.listKeys(KEY_PREFIX).map(key -> key.substring(KEY_PREFIX.length()));
    }

    /**
     * Delete a checkpoint by ID.
     *
     * @param checkpointId the checkpoint to remove
     * @return a Mono completing when the checkpoint is deleted
     */
    public Mono<Void> deleteCheckpoint(String checkpointId) {
        if (checkpointId == null || checkpointId.isBlank()) {
            return Mono.error(
                    new IllegalArgumentException("checkpointId must not be null or blank"));
        }
        return snapshotStore.delete(KEY_PREFIX + checkpointId);
    }
}
