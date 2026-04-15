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
package io.kairo.tools.agent;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.ToolHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spawns a sub-agent to handle a specific task autonomously.
 *
 * <p>The sub-agent is created from the parent agent's configuration as a template, with a custom
 * system prompt and task description. The sub-agent runs to completion and its result is returned
 * to the parent agent.
 *
 * <p>This enables hierarchical agent delegation: a parent agent can break complex work into
 * sub-tasks and delegate each to a specialized sub-agent.
 */
@Tool(
        name = "agent_spawn",
        description = "Spawn a sub-agent to handle a specific task autonomously.",
        category = ToolCategory.AGENT_AND_TASK)
public class AgentSpawnTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentSpawnTool.class);

    @ToolParam(description = "Name for the sub-agent", required = true)
    private String name;

    @ToolParam(description = "Task description for the sub-agent", required = true)
    private String task;

    @ToolParam(description = "System prompt for the sub-agent")
    private String systemPrompt;

    private final AgentFactory agentFactory;
    private final AgentConfig baseConfig;

    /**
     * Create a new AgentSpawnTool.
     *
     * @param agentFactory the factory for creating sub-agents
     * @param baseConfig the parent agent's config used as a template
     */
    public AgentSpawnTool(AgentFactory agentFactory, AgentConfig baseConfig) {
        this.agentFactory = agentFactory;
        this.baseConfig = baseConfig;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String name = (String) input.get("name");
        String task = (String) input.get("task");
        String prompt = (String) input.getOrDefault("systemPrompt", "You are a helpful sub-agent.");

        if (name == null || name.isBlank()) {
            return new ToolResult(null, "Parameter 'name' is required", true, Map.of());
        }
        if (task == null || task.isBlank()) {
            return new ToolResult(null, "Parameter 'task' is required", true, Map.of());
        }

        try {
            // Build sub-agent config from parent config template
            AgentConfig subConfig =
                    AgentConfig.builder()
                            .name(name)
                            .systemPrompt(prompt)
                            .modelProvider(baseConfig.modelProvider())
                            .toolRegistry(baseConfig.toolRegistry())
                            .maxIterations(baseConfig.maxIterations())
                            .timeout(baseConfig.timeout())
                            .tokenBudget(baseConfig.tokenBudget())
                            .build();

            Agent subAgent = agentFactory.create(subConfig);
            log.info("Spawned sub-agent '{}' for task: {}", name, task);

            // Execute sub-agent synchronously (blocks until complete)
            Msg taskMsg = Msg.of(MsgRole.USER, task);
            Msg result = subAgent.call(taskMsg).block();

            String resultText =
                    result != null ? extractText(result) : "Sub-agent completed with no output";
            log.info("Sub-agent '{}' completed", name);
            return new ToolResult(
                    null,
                    String.format("Sub-agent '%s' result:\n%s", name, resultText),
                    false,
                    Map.of("subAgentName", name));

        } catch (Exception e) {
            log.error("Sub-agent '{}' failed: {}", name, e.getMessage(), e);
            return new ToolResult(
                    null,
                    String.format("Sub-agent '%s' failed: %s", name, e.getMessage()),
                    true,
                    Map.of());
        }
    }

    /**
     * Extract text content from a message.
     *
     * @param msg the message to extract text from
     * @return the extracted text content
     */
    private String extractText(Msg msg) {
        StringBuilder sb = new StringBuilder();
        for (Content content : msg.contents()) {
            if (content instanceof Content.TextContent tc) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(tc.text());
            }
        }
        return sb.isEmpty() ? msg.toString() : sb.toString();
    }
}
