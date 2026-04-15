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
package io.kairo.core.agent;

import io.kairo.api.agent.AgentConfig;
import java.util.List;

/**
 * Configuration for a Coordinator Agent that orchestrates worker agents without directly executing
 * file/exec tools.
 *
 * @param baseConfig the underlying agent configuration
 * @param workerTemplates templates for spawning worker agents
 * @param maxConcurrentWorkers maximum number of workers running simultaneously
 * @param requirePlanBeforeDispatch if true, coordinator must create a plan before spawning workers
 */
public record CoordinatorConfig(
        AgentConfig baseConfig,
        List<AgentConfig> workerTemplates,
        int maxConcurrentWorkers,
        boolean requirePlanBeforeDispatch) {

    public CoordinatorConfig {
        if (maxConcurrentWorkers <= 0) {
            throw new IllegalArgumentException("maxConcurrentWorkers must be > 0");
        }
    }

    /** Create with defaults: 5 workers, plan required. */
    public static CoordinatorConfig of(AgentConfig baseConfig) {
        return new CoordinatorConfig(baseConfig, List.of(), 5, true);
    }

    /** Create with worker templates. */
    public static CoordinatorConfig of(AgentConfig baseConfig, List<AgentConfig> workerTemplates) {
        return new CoordinatorConfig(baseConfig, workerTemplates, 5, true);
    }

    /** Builder for more control. */
    public static Builder builder(AgentConfig baseConfig) {
        return new Builder(baseConfig);
    }

    /** Builder for {@link CoordinatorConfig}. */
    public static class Builder {
        private final AgentConfig baseConfig;
        private List<AgentConfig> workerTemplates = List.of();
        private int maxConcurrentWorkers = 5;
        private boolean requirePlanBeforeDispatch = true;

        private Builder(AgentConfig baseConfig) {
            this.baseConfig = baseConfig;
        }

        public Builder workerTemplates(List<AgentConfig> templates) {
            this.workerTemplates = templates;
            return this;
        }

        public Builder maxConcurrentWorkers(int max) {
            this.maxConcurrentWorkers = max;
            return this;
        }

        public Builder requirePlanBeforeDispatch(boolean require) {
            this.requirePlanBeforeDispatch = require;
            return this;
        }

        public CoordinatorConfig build() {
            return new CoordinatorConfig(
                    baseConfig, workerTemplates, maxConcurrentWorkers, requirePlanBeforeDispatch);
        }
    }
}
