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
package io.kairo.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.AgentState;
import io.kairo.core.health.AgentHealthInfo;
import io.kairo.core.health.AgentHealthRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KairoAgentsEndpointTest {

    private final KairoAgentsEndpoint endpoint = new KairoAgentsEndpoint();

    @BeforeEach
    @AfterEach
    void cleanRegistry() {
        AgentHealthRegistry.global().deregisterAll();
    }

    @Test
    void emptyRegistry_returnsEmptyAgentsList() {
        Map<String, Object> result = endpoint.agents();

        assertThat(result).containsKeys("agents", "count");
        assertThat((List<?>) result.get("agents")).isEmpty();
        assertThat(result.get("count")).isEqualTo(0);
    }

    @Test
    void oneRunningAgent_appearsInSnapshot() {
        AgentHealthRegistry.global()
                .register(
                        "agent-1",
                        () ->
                                new AgentHealthInfo(
                                        "agent-1", "my-agent", AgentState.RUNNING, 3, null));

        Map<String, Object> result = endpoint.agents();
        List<?> agents = (List<?>) result.get("agents");

        assertThat(agents).hasSize(1);
        Map<?, ?> entry = (Map<?, ?>) agents.get(0);
        assertThat(entry.get("agentId")).isEqualTo("agent-1");
        assertThat(entry.get("name")).isEqualTo("my-agent");
        assertThat(entry.get("state")).isEqualTo("RUNNING");
        assertThat(entry.get("iterationCount")).isEqualTo(3);
    }

    @Test
    void twoActiveAgents_countIsTwo() {
        AgentHealthRegistry.global()
                .register(
                        "a1",
                        () -> new AgentHealthInfo("a1", "alpha", AgentState.RUNNING, 1, null));
        AgentHealthRegistry.global()
                .register("a2", () -> new AgentHealthInfo("a2", "beta", AgentState.IDLE, 0, null));

        Map<String, Object> result = endpoint.agents();

        assertThat(result.get("count")).isEqualTo(2);
        assertThat((List<?>) result.get("agents")).hasSize(2);
    }

    @Test
    void completedAgent_isEvictedFromSnapshot() {
        AgentHealthRegistry.global()
                .register(
                        "done-agent",
                        () ->
                                new AgentHealthInfo(
                                        "done-agent", "old", AgentState.COMPLETED, 5, null));

        Map<String, Object> result = endpoint.agents();

        assertThat((List<?>) result.get("agents")).isEmpty();
        assertThat(result.get("count")).isEqualTo(0);
    }

    @Test
    void failedAgent_isEvictedFromSnapshot() {
        AgentHealthRegistry.global()
                .register(
                        "failed-agent",
                        () ->
                                new AgentHealthInfo(
                                        "failed-agent", "crash", AgentState.FAILED, 2, null));

        Map<String, Object> result = endpoint.agents();

        assertThat((List<?>) result.get("agents")).isEmpty();
    }

    @Test
    void lastActivityAt_serializedAsString() {
        Instant now = Instant.parse("2026-04-27T10:00:00Z");
        AgentHealthRegistry.global()
                .register(
                        "ts-agent",
                        () -> new AgentHealthInfo("ts-agent", "timer", AgentState.RUNNING, 0, now));

        Map<String, Object> result = endpoint.agents();
        Map<?, ?> entry = (Map<?, ?>) ((List<?>) result.get("agents")).get(0);

        assertThat(entry.get("lastActivityAt")).isEqualTo("2026-04-27T10:00:00Z");
    }

    @Test
    void lastActivityAt_null_serializedAsNull() {
        AgentHealthRegistry.global()
                .register(
                        "no-ts",
                        () -> new AgentHealthInfo("no-ts", "agent", AgentState.RUNNING, 0, null));

        Map<?, ?> entry = (Map<?, ?>) ((List<?>) endpoint.agents().get("agents")).get(0);

        assertThat(entry.get("lastActivityAt")).isNull();
    }

    @Test
    void responseMap_alwaysContainsAgentsAndCountKeys() {
        Map<String, Object> result = endpoint.agents();

        assertThat(result.keySet()).containsExactlyInAnyOrder("agents", "count");
    }
}
