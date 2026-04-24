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
package io.kairo.api.agent;

import io.kairo.api.Stable;

/** Factory for creating agent instances from configuration. */
@Stable(value = "Agent factory SPI; shape frozen since v0.4", since = "1.0.0")
public interface AgentFactory {

    /**
     * Create a new agent from the given configuration.
     *
     * @param config the agent configuration
     * @return a new agent instance
     */
    Agent create(AgentConfig config);

    /**
     * Create a sub-agent that inherits context from a parent agent.
     *
     * @param parent the parent agent
     * @param config the sub-agent configuration
     * @return a new sub-agent instance
     */
    Agent createSubAgent(Agent parent, AgentConfig config);
}
