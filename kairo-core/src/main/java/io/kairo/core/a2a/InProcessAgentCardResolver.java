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
package io.kairo.core.a2a;

import io.kairo.api.a2a.AgentCard;
import io.kairo.api.a2a.AgentCardResolver;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link AgentCardResolver} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Thread-safe. Suitable for single-JVM deployments and testing. For distributed discovery, use a
 * Nacos/etcd-backed implementation (v0.5+).
 */
public final class InProcessAgentCardResolver implements AgentCardResolver {

    private final Map<String, AgentCard> registry = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentCard> resolve(String agentId) {
        return Optional.ofNullable(registry.get(agentId));
    }

    @Override
    public List<AgentCard> discover(Set<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) {
            return List.copyOf(registry.values());
        }
        return registry.values().stream()
                .filter(card -> new HashSet<>(card.tags()).containsAll(requiredTags))
                .toList();
    }

    @Override
    public List<AgentCard> listAll() {
        return List.copyOf(registry.values());
    }

    @Override
    public void register(AgentCard card) {
        Objects.requireNonNull(card, "card must not be null");
        registry.put(card.id(), card);
    }

    @Override
    public void unregister(String agentId) {
        registry.remove(agentId);
    }
}
