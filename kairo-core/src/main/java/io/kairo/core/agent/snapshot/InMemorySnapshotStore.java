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

import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.SnapshotStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory {@link SnapshotStore} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Suitable for testing and single-JVM deployments. Snapshots are lost when the process exits.
 */
public final class InMemorySnapshotStore implements SnapshotStore {

    private final Map<String, AgentSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(String key, AgentSnapshot snapshot) {
        store.put(key, snapshot);
        return Mono.empty();
    }

    @Override
    public Mono<AgentSnapshot> load(String key) {
        return Mono.justOrEmpty(store.get(key));
    }

    @Override
    public Mono<Void> delete(String key) {
        store.remove(key);
        return Mono.empty();
    }

    @Override
    public Flux<String> listKeys(String agentIdPrefix) {
        String prefix = agentIdPrefix != null ? agentIdPrefix : "";
        return Flux.fromStream(store.keySet().stream().filter(k -> k.startsWith(prefix)));
    }
}
