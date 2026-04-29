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
package io.kairo.api.agent;

import io.kairo.api.Stable;
import io.kairo.api.message.Msg;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Pluggable storage SPI for iteration-level checkpoint persistence.
 *
 * <p>Unlike {@link SnapshotStore} which captures full agent snapshots at named savepoints, this
 * interface focuses on lightweight per-iteration checkpoints written after each successful tool
 * execution. This enables crash recovery from the last completed iteration rather than replaying
 * from scratch.
 *
 * <p>Implementations are responsible for automatic pruning of old checkpoints to prevent unbounded
 * disk growth.
 *
 * @see IterationCheckpoint
 */
@Stable(value = "Iteration checkpoint persistence SPI", since = "1.0.0")
public interface IterationCheckpointStore {

    /**
     * Save a checkpoint for the given iteration.
     *
     * <p>Overwrites any existing checkpoint for the same iteration index. Implementations should
     * automatically prune old checkpoints beyond the retention limit.
     *
     * @param iteration the zero-based iteration index
     * @param messages the full conversation history at this point
     * @return a Mono completing when the checkpoint is persisted
     */
    Mono<Void> save(int iteration, List<Msg> messages);

    /**
     * Load the last saved iteration checkpoint.
     *
     * @return a Mono emitting the latest checkpoint, or empty if none exists
     */
    Mono<Optional<IterationCheckpoint>> loadLast();

    /**
     * Delete all checkpoints managed by this store.
     *
     * @return a Mono completing when all checkpoints are deleted
     */
    Mono<Void> deleteAll();
}
