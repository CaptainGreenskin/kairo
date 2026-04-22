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
package io.kairo.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Checkpoint configuration ({@code kairo.checkpoint.*}).
 *
 * <p>Controls agent state snapshot and restoration. When enabled, the agent's conversational state
 * can be serialized mid-conversation and restored later via {@code
 * AgentBuilder.restoreFrom(snapshot)}.
 */
@ConfigurationProperties(prefix = "kairo.checkpoint")
public class CheckpointProperties {

    /**
     * Whether checkpoint management is enabled. When enabled, agents can create and restore from
     * state snapshots for durable execution and recovery scenarios.
     *
     * <p>Default: {@code false}
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
