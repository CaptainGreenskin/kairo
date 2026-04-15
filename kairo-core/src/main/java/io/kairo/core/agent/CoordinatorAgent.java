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
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Coordinator Agent that orchestrates worker agents without directly executing file, exec, or
 * search tools.
 *
 * <p>The coordinator is stripped of all "hands-on" tools and only retains orchestration tools
 * (AGENT_AND_TASK category). This enforces the "understand first, delegate second" principle.
 */
public class CoordinatorAgent extends DefaultReActAgent {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorAgent.class);

    private static final String COORDINATOR_PROMPT_PREFIX =
            """
        You are a Coordinator Agent. You MUST NOT directly read files, write code, or execute commands.

        Your role is to:
        1. Understand the task thoroughly before taking action
        2. Break complex work into well-defined sub-tasks
        3. Create a plan using enter_plan_mode / exit_plan_mode
        4. Spawn worker agents for each sub-task using agent_spawn
        5. Monitor progress via task_list / task_get
        6. Synthesize results into a coherent response

        Rules:
        - Always create a plan before spawning workers
        - Each worker should have a clear, bounded scope
        - Provide workers with specific instructions and expected outputs
        - Do not attempt to do the work yourself — delegate everything
        """;

    /** Allowed tool categories for the coordinator. */
    private static final Set<ToolCategory> ALLOWED_CATEGORIES = Set.of(ToolCategory.AGENT_AND_TASK);

    private final CoordinatorConfig coordinatorConfig;

    public CoordinatorAgent(CoordinatorConfig config) {
        super(
                buildFilteredConfig(config),
                buildFilteredToolExecutor(config),
                new DefaultHookChain());
        this.coordinatorConfig = config;
        log.info(
                "CoordinatorAgent '{}' created with orchestration-only tool filtering",
                config.baseConfig().name());
    }

    /** Filter the tool registry to only keep orchestration tools and build a new AgentConfig. */
    private static AgentConfig buildFilteredConfig(CoordinatorConfig coordConfig) {
        AgentConfig base = coordConfig.baseConfig();

        // Filter tools: only AGENT_AND_TASK category
        ToolRegistry filteredRegistry = filterRegistry(base.toolRegistry());

        // Prepend coordinator instructions to system prompt
        String enhancedPrompt = COORDINATOR_PROMPT_PREFIX;
        if (base.systemPrompt() != null && !base.systemPrompt().isBlank()) {
            enhancedPrompt = COORDINATOR_PROMPT_PREFIX + "\n\n" + base.systemPrompt();
        }

        // Add plan requirement instruction if configured
        if (coordConfig.requirePlanBeforeDispatch()) {
            enhancedPrompt +=
                    "\n\nIMPORTANT: You MUST use enter_plan_mode to create a plan before spawning"
                            + " any worker agents.";
        }

        // Rebuild config with filtered registry and enhanced prompt
        AgentConfig.Builder builder =
                AgentConfig.builder()
                        .name(base.name() != null ? base.name() : "coordinator")
                        .systemPrompt(enhancedPrompt)
                        .modelProvider(base.modelProvider())
                        .toolRegistry(filteredRegistry)
                        .maxIterations(base.maxIterations())
                        .timeout(base.timeout())
                        .tokenBudget(base.tokenBudget())
                        .modelName(base.modelName())
                        .contextManager(base.contextManager())
                        .memoryStore(base.memoryStore())
                        .sessionId(base.sessionId());

        // Carry over hooks from base config
        if (base.hooks() != null) {
            for (Object hook : base.hooks()) {
                builder.addHook(hook);
            }
        }

        return builder.build();
    }

    /** Build a tool executor wired to the filtered registry. */
    private static DefaultToolExecutor buildFilteredToolExecutor(CoordinatorConfig coordConfig) {
        AgentConfig base = coordConfig.baseConfig();
        DefaultToolRegistry filteredRegistry = filterRegistryAsDefault(base.toolRegistry());
        return new DefaultToolExecutor(filteredRegistry, new DefaultPermissionGuard());
    }

    /** Create a filtered {@link DefaultToolRegistry} containing only orchestration tools. */
    private static DefaultToolRegistry filterRegistryAsDefault(ToolRegistry source) {
        DefaultToolRegistry filtered = new DefaultToolRegistry();
        if (source == null) {
            return filtered;
        }

        List<ToolDefinition> allTools = source.getAll();
        int total = allTools.size();
        int kept = 0;

        for (ToolDefinition tool : allTools) {
            if (ALLOWED_CATEGORIES.contains(tool.category())) {
                filtered.register(tool);
                // Copy tool instance if source is a DefaultToolRegistry
                if (source instanceof DefaultToolRegistry sourceDefault) {
                    Object instance = sourceDefault.getToolInstance(tool.name());
                    if (instance != null) {
                        filtered.registerInstance(tool.name(), instance);
                    }
                }
                kept++;
            }
        }

        log.debug(
                "Tool filtering: kept {}/{} tools (categories: {})",
                kept,
                total,
                ALLOWED_CATEGORIES);
        return filtered;
    }

    /** Create a filtered registry as the {@link ToolRegistry} interface. */
    private static ToolRegistry filterRegistry(ToolRegistry source) {
        return filterRegistryAsDefault(source);
    }

    /** Get the coordinator-specific configuration. */
    public CoordinatorConfig coordinatorConfig() {
        return coordinatorConfig;
    }
}
