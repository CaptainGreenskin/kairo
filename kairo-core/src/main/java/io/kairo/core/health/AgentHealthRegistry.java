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

/**
 * Process-global registry of live agent health suppliers.
 *
 * <p>Agents register a {@link Supplier} that returns their current {@link AgentHealthInfo} on
 * demand, so callers always get a fresh snapshot without polling.
 */
public final class AgentHealthRegistry {

    private static final AgentHealthRegistry INSTANCE = new AgentHealthRegistry();

    private final ConcurrentHashMap<String, Supplier<AgentHealthInfo>> suppliers =
            new ConcurrentHashMap<>();

    private AgentHealthRegistry() {}

    public static AgentHealthRegistry global() {
        return INSTANCE;
    }

    public void register(String agentId, Supplier<AgentHealthInfo> infoSupplier) {
        suppliers.put(agentId, infoSupplier);
    }

    public void deregister(String agentId) {
        suppliers.remove(agentId);
    }

    public void deregisterAll() {
        suppliers.clear();
    }

    /**
     * Returns a snapshot of all currently active agents, auto-evicting entries whose suppliers
     * throw or whose state is terminal (COMPLETED / FAILED).
     */
    public List<AgentHealthInfo> snapshot() {
        List<AgentHealthInfo> result = new ArrayList<>();
        List<String> toEvict = new ArrayList<>();

        suppliers.forEach(
                (agentId, supplier) -> {
                    try {
                        AgentHealthInfo info = supplier.get();
                        if (info == null
                                || info.state() == AgentState.COMPLETED
                                || info.state() == AgentState.FAILED) {
                            toEvict.add(agentId);
                        } else {
                            result.add(info);
                        }
                    } catch (Exception e) {
                        toEvict.add(agentId);
                    }
                });

        toEvict.forEach(suppliers::remove);
        return result;
    }
}
