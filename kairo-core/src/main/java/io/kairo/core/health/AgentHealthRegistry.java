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
package io.kairo.core.health;

import io.kairo.api.agent.AgentState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Global registry of live agent health suppliers. Thread-safe, singleton. */
public final class AgentHealthRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthRegistry.class);
    private static final AgentHealthRegistry INSTANCE = new AgentHealthRegistry();

    private final ConcurrentHashMap<String, Supplier<AgentHealthInfo>> suppliers =
            new ConcurrentHashMap<>();

    private AgentHealthRegistry() {}

    public static AgentHealthRegistry global() {
        return INSTANCE;
    }

    /** Register a live supplier for {@code agentId}. Idempotent — replaces any existing entry. */
    public void register(String agentId, Supplier<AgentHealthInfo> infoSupplier) {
        suppliers.put(agentId, infoSupplier);
    }

    /** Remove the registration for {@code agentId}. Idempotent — no-op if not registered. */
    public void deregister(String agentId) {
        suppliers.remove(agentId);
    }

    /** Remove all registrations. Useful for testing and Spring context shutdown. */
    public void deregisterAll() {
        suppliers.clear();
    }

    /**
     * Snapshot all currently registered agents. Suppliers that throw or return terminal states
     * (COMPLETED/FAILED) are silently evicted from the registry.
     */
    public List<AgentHealthInfo> snapshot() {
        List<AgentHealthInfo> results = new ArrayList<>(suppliers.size());
        suppliers.forEach(
                (agentId, supplier) -> {
                    try {
                        AgentHealthInfo info = supplier.get();
                        if (info != null) {
                            if (info.state() == AgentState.COMPLETED
                                    || info.state() == AgentState.FAILED) {
                                suppliers.remove(agentId);
                            } else {
                                results.add(info);
                            }
                        }
                    } catch (Exception e) {
                        log.warn(
                                "Health supplier for agent '{}' threw: {}",
                                agentId,
                                e.getMessage());
                        suppliers.remove(agentId);
                    }
                });
        return results;
    }
}
