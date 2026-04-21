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

import io.kairo.api.a2a.A2aNamespaces;
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

    private static final String GLOBAL_NAMESPACE = "__global__";
    private final Map<String, AgentCard> registry = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentCard> resolve(String agentId) {
        return resolveScoped(GLOBAL_NAMESPACE, agentId);
    }

    @Override
    public List<AgentCard> discover(Set<String> requiredTags) {
        return discoverScoped(GLOBAL_NAMESPACE, requiredTags);
    }

    public Optional<AgentCard> resolveScoped(String namespace, String agentId) {
        return Optional.ofNullable(registry.get(scopedKey(namespace, agentId)));
    }

    public List<AgentCard> discoverScoped(String namespace, Set<String> requiredTags) {
        String nsPrefix = namespacePrefix(namespace);
        List<AgentCard> cards =
                registry.entrySet().stream()
                        .filter(e -> e.getKey().startsWith(nsPrefix))
                        .map(Map.Entry::getValue)
                        .toList();
        if (requiredTags == null || requiredTags.isEmpty()) {
            return cards;
        }
        return cards.stream()
                .filter(card -> new HashSet<>(card.tags()).containsAll(requiredTags))
                .toList();
    }

    @Override
    public List<AgentCard> listAll() {
        return listAllScoped(GLOBAL_NAMESPACE);
    }

    public List<AgentCard> listAllScoped(String namespace) {
        String nsPrefix = namespacePrefix(namespace);
        return registry.entrySet().stream()
                .filter(e -> e.getKey().startsWith(nsPrefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public void register(AgentCard card) {
        registerScoped(GLOBAL_NAMESPACE, card);
    }

    public void registerScoped(String namespace, AgentCard card) {
        Objects.requireNonNull(card, "card must not be null");
        registry.put(scopedKey(namespace, card.id()), card);
    }

    @Override
    public void unregister(String agentId) {
        unregisterScoped(GLOBAL_NAMESPACE, agentId);
    }

    public void unregisterScoped(String namespace, String agentId) {
        registry.remove(scopedKey(namespace, agentId));
    }

    private String scopedKey(String namespace, String agentId) {
        return A2aNamespaces.scoped(namespaceOrGlobal(namespace), agentId);
    }

    private String namespacePrefix(String namespace) {
        return namespaceOrGlobal(namespace) + ":";
    }

    private String namespaceOrGlobal(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return GLOBAL_NAMESPACE;
        }
        return namespace;
    }
}
