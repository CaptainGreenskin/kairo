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

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.middleware.DefaultMiddlewarePipeline;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link AgentFactory} that creates {@link DefaultReActAgent} instances.
 */
public class DefaultAgentFactory implements AgentFactory {

    private final ToolExecutor toolExecutor;
    private final GracefulShutdownManager shutdownManager;

    /**
     * Create a factory with the given tool executor.
     *
     * @param toolExecutor the executor used by created agents
     */
    public DefaultAgentFactory(ToolExecutor toolExecutor) {
        this(toolExecutor, null);
    }

    /**
     * Create a factory with the given tool executor and shutdown manager.
     *
     * @param toolExecutor the executor used by created agents
     * @param shutdownManager the graceful shutdown manager (null creates a new instance per agent)
     */
    public DefaultAgentFactory(ToolExecutor toolExecutor, GracefulShutdownManager shutdownManager) {
        this.toolExecutor = toolExecutor;
        this.shutdownManager = shutdownManager;
    }

    @Override
    public Agent create(AgentConfig config) {
        DefaultHookChain hookChain = new DefaultHookChain();
        DefaultMiddlewarePipeline pipeline =
                new DefaultMiddlewarePipeline(
                        config.middlewares() != null ? config.middlewares() : List.of());
        GracefulShutdownManager sm =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();
        return new DefaultReActAgent(config, toolExecutor, hookChain, pipeline, sm);
    }

    @Override
    public Agent createSubAgent(Agent parent, AgentConfig config) {
        DefaultHookChain hookChain = new DefaultHookChain();
        DefaultMiddlewarePipeline pipeline =
                new DefaultMiddlewarePipeline(
                        config.middlewares() != null ? config.middlewares() : List.of());
        GracefulShutdownManager sm =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();

        // Inherit parent's conversation history for context
        List<io.kairo.api.message.Msg> parentContext = new ArrayList<>();
        if (parent instanceof DefaultReActAgent reactAgent) {
            parentContext.addAll(reactAgent.conversationHistory());
        }

        return new DefaultReActAgent(config, toolExecutor, hookChain, pipeline, sm, parentContext);
    }
}
