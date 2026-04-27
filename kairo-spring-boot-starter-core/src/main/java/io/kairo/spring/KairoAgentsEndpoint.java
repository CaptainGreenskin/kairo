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

import io.kairo.core.health.AgentHealthInfo;
import io.kairo.core.health.AgentHealthRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Spring Actuator endpoint exposing all live agents at {@code /actuator/kairo-agents}.
 *
 * <p>Each entry includes agentId, name, state, iterationCount, and lastActivityAt. Terminal agents
 * (COMPLETED/FAILED) are automatically evicted by the registry during the snapshot.
 */
@Endpoint(id = "kairo-agents")
public class KairoAgentsEndpoint {

    @ReadOperation
    public Map<String, Object> agents() {
        List<AgentHealthInfo> infos = AgentHealthRegistry.global().snapshot();
        List<Map<String, Object>> agentViews =
                infos.stream()
                        .map(
                                info ->
                                        Map.<String, Object>of(
                                                "agentId",
                                                info.agentId(),
                                                "name",
                                                info.name(),
                                                "state",
                                                info.state().name(),
                                                "iterationCount",
                                                info.iterationCount(),
                                                "lastActivityAt",
                                                info.lastActivityAt() != null
                                                        ? info.lastActivityAt().toString()
                                                        : null))
                        .collect(Collectors.toList());

        return Map.of("agents", agentViews, "count", agentViews.size());
    }
}
