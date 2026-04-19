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
package io.kairo.api.a2a;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Discovery SPI for locating agents by their {@link AgentCard}.
 *
 * <p>Implementations provide agent card storage and lookup. The default in-process implementation
 * uses a {@code ConcurrentHashMap}-backed registry. Future implementations may integrate with
 * service discovery systems (Nacos, etcd, Consul).
 *
 * <p>Tag-based discovery uses AND semantics: {@code discover(Set.of("java", "code-review"))} only
 * returns agents whose card contains <em>all</em> specified tags. This is consistent with {@code
 * MemoryStore} tag filtering.
 *
 * @see AgentCard
 */
public interface AgentCardResolver {

    /**
     * Resolve an agent card by its unique identifier.
     *
     * @param agentId the agent identifier
     * @return the agent card, or empty if not found
     */
    Optional<AgentCard> resolve(String agentId);

    /**
     * Discover agents whose card contains all the specified tags (AND matching).
     *
     * @param requiredTags tags that must all be present on the agent card
     * @return list of matching agent cards; empty if none found
     */
    List<AgentCard> discover(Set<String> requiredTags);

    /**
     * List all registered agent cards.
     *
     * @return unmodifiable list of all registered cards
     */
    List<AgentCard> listAll();

    /**
     * Register an agent card for discovery.
     *
     * @param card the agent card to register
     */
    void register(AgentCard card);

    /**
     * Unregister an agent card by its identifier.
     *
     * @param agentId the agent identifier to remove
     */
    void unregister(String agentId);
}
