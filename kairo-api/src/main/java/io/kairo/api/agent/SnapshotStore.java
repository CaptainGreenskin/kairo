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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Pluggable storage SPI for {@link AgentSnapshot} persistence.
 *
 * <p>Implementations provide storage backends for saving and loading agent snapshots. The default
 * in-process implementation uses a {@code ConcurrentHashMap}. Additional implementations (file,
 * JDBC, Redis) can be provided as separate modules.
 *
 * @see AgentSnapshot
 */
@Stable(value = "Snapshot persistence SPI; shape frozen since v0.4", since = "1.0.0")
public interface SnapshotStore {

    /**
     * Save a snapshot under the given key.
     *
     * @param key the storage key (typically agent ID or a composite key)
     * @param snapshot the snapshot to persist
     * @return a Mono completing when the save is done
     */
    Mono<Void> save(String key, AgentSnapshot snapshot);

    /**
     * Load a snapshot by key.
     *
     * @param key the storage key
     * @return a Mono emitting the snapshot, or empty if not found
     */
    Mono<AgentSnapshot> load(String key);

    /**
     * Delete a snapshot by key.
     *
     * @param key the storage key to remove
     * @return a Mono completing when the delete is done
     */
    Mono<Void> delete(String key);

    /**
     * List all snapshot keys matching the given agent ID prefix.
     *
     * @param agentIdPrefix prefix to filter by; use empty string for all
     * @return a Flux of matching keys
     */
    Flux<String> listKeys(String agentIdPrefix);
}
