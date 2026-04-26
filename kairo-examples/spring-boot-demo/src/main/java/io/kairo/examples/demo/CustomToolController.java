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
package io.kairo.examples.demo;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.core.agent.AgentBuilder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating custom tool registration and usage with Kairo agents.
 *
 * <p>This controller registers {@link WeatherTool} and {@link CalculatorTool} into the tool
 * registry, builds an agent equipped with these tools, and exposes endpoints to interact with the
 * agent and inspect available tools.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * # Chat with the tool-equipped agent
 * curl -X POST http://localhost:8080/tools/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "What is the weather in Beijing?"}'
 *
 * # List all registered tools
 * curl http://localhost:8080/tools/list
 * }</pre>
 */
@RestController
@RequestMapping("/tools")
public class CustomToolController {

    private final Agent agent;
    private final ToolRegistry toolRegistry;
    private final String modelName;

    /**
     * Construct the controller, registering custom tools and building a tool-equipped agent.
     *
     * @param modelProvider the model provider for LLM calls
     * @param toolRegistry the tool registry to register custom tools into
     * @param toolExecutor the tool executor for running tools
     */
    public CustomToolController(
            ModelProvider modelProvider,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            @Value("${kairo.model.model-name:qwen-plus}") String modelName) {
        this.toolRegistry = toolRegistry;
        this.modelName = modelName;

        // Register custom tools via classpath scan of the demo package
        toolRegistry.scan("io.kairo.examples.demo");

        // Build a dedicated agent with access to all registered tools
        this.agent =
                AgentBuilder.create()
                        .name("custom-tool-agent")
                        .model(modelProvider)
                        .modelName(modelName)
                        .tools(toolRegistry)
                        .toolExecutor(toolExecutor)
                        .systemPrompt(
                                "You are a helpful assistant with access to weather lookup "
                                        + "and calculator tools. Use them when the user asks about weather "
                                        + "or needs arithmetic calculations. Always use the appropriate tool "
                                        + "rather than guessing.")
                        .maxIterations(10)
                        .tokenBudget(50_000)
                        .build();
    }

    /**
     * Chat with the agent that has access to custom tools.
     *
     * @param request the chat request containing the user message
     * @return a JSON response with the agent's reply
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequest request) {
        Msg input = Msg.of(MsgRole.USER, request.message());
        Msg response = agent.call(input).block();
        String reply = (response != null) ? response.text() : "No response";
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    /**
     * List all registered tool definitions with their names, descriptions, and categories.
     *
     * @return a JSON response containing a list of tool summaries
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listTools() {
        List<Map<String, String>> tools =
                toolRegistry.getAll().stream()
                        .map(
                                tool ->
                                        Map.of(
                                                "name", tool.name(),
                                                "description", tool.description(),
                                                "category", tool.category().name()))
                        .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("tools", tools, "count", tools.size()));
    }

    /** Simple request body for the chat endpoint. */
    public record ChatRequest(String message) {}
}
