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
package io.kairo.core.agent.checkpoint;

import io.kairo.api.agent.IterationCheckpoint;
import io.kairo.api.agent.IterationCheckpointStore;
import io.kairo.api.message.Msg;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Manages iteration-level checkpoints for crash recovery.
 *
 * <p>After each successful tool execution iteration, the caller should invoke {@link #save} to
 * persist the current conversation history. On resume, {@link #loadLast} retrieves the latest
 * checkpoint so the agent can continue from where it left off.
 *
 * <p>This is a thin orchestration layer over {@link IterationCheckpointStore} that adds logging and
 * validation.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * IterationCheckpointManager mgr = new IterationCheckpointManager(
 *     new JsonFileIterationCheckpointStore(path, serializer));
 * mgr.save(iteration, messages).block();
 * Optional<IterationCheckpoint> restored = mgr.loadLast().block();
 * }</pre>
 */
public class IterationCheckpointManager {

    private static final Logger log = LoggerFactory.getLogger(IterationCheckpointManager.class);

    private final IterationCheckpointStore store;

    /**
     * Create an IterationCheckpointManager backed by the given store.
     *
     * @param store the underlying checkpoint storage backend
     * @throws IllegalArgumentException if store is null
     */
    public IterationCheckpointManager(IterationCheckpointStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    /**
     * Save a checkpoint for the given iteration.
     *
     * @param iteration the zero-based iteration index
     * @param messages the full conversation history at this point
     * @return a Mono completing when the checkpoint is persisted
     */
    public Mono<Void> save(int iteration, List<Msg> messages) {
        if (messages == null) {
            return Mono.error(new IllegalArgumentException("messages must not be null"));
        }
        log.debug("Saving iteration checkpoint at iteration {}", iteration);
        return store.save(iteration, messages)
                .doOnSuccess(
                        v ->
                                log.debug(
                                        "Saved iteration checkpoint {} ({} messages)",
                                        iteration,
                                        messages.size()));
    }

    /**
     * Load the last saved iteration checkpoint.
     *
     * @return a Mono emitting the latest checkpoint, or empty if none exists
     */
    public Mono<Optional<IterationCheckpoint>> loadLast() {
        return store.loadLast()
                .doOnNext(
                        opt -> {
                            if (opt.isPresent()) {
                                IterationCheckpoint cp = opt.get();
                                log.info(
                                        "Loaded last iteration checkpoint: iteration={}, messages={}",
                                        cp.iteration(),
                                        cp.messages().size());
                            } else {
                                log.debug("No iteration checkpoint found");
                            }
                        });
    }

    /**
     * Delete all checkpoints managed by this instance.
     *
     * @return a Mono completing when all checkpoints are deleted
     */
    public Mono<Void> deleteAll() {
        return store.deleteAll().doOnSuccess(v -> log.debug("Deleted all iteration checkpoints"));
    }
}
