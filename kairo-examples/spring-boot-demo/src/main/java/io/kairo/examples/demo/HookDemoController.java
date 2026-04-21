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
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating Kairo hook capabilities for observability and auditing.
 *
 * <p>This controller builds an agent with {@link TimingHook} and {@link AuditHook} attached, and
 * exposes endpoints to interact with the agent and inspect the collected metrics and audit log.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * # Chat with the hook-instrumented agent
 * curl -X POST http://localhost:8080/hooks/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "What is the weather in Tokyo?"}'
 *
 * # View collected metrics and audit log
 * curl http://localhost:8080/hooks/metrics
 *
 * # Reset all metrics
 * curl -X DELETE http://localhost:8080/hooks/reset
 * }</pre>
 */
@RestController
@RequestMapping("/hooks")
public class HookDemoController {

    private final Agent agent;
    private final TimingHook timingHook;
    private final AuditHook auditHook;
    private final String modelName;

    /**
     * Construct the controller, building an agent with timing and audit hooks.
     *
     * @param modelProvider the model provider for LLM calls
     * @param toolRegistry the tool registry for available tools
     * @param toolExecutor the tool executor for running tools
     */
    public HookDemoController(
            ModelProvider modelProvider,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            @Value("${kairo.model.model-name:qwen-plus}") String modelName) {
        this.timingHook = new TimingHook();
        this.auditHook = new AuditHook();
        this.modelName = modelName;

        this.agent =
                AgentBuilder.create()
                        .name("hook-demo-agent")
                        .model(modelProvider)
                        .modelName(modelName)
                        .tools(toolRegistry)
                        .toolExecutor(toolExecutor)
                        .systemPrompt(
                                "You are a helpful assistant. Use available tools when "
                                        + "appropriate. Your interactions are being monitored for timing "
                                        + "and audit purposes.")
                        .maxIterations(10)
                        .tokenBudget(50_000)
                        .hook(timingHook)
                        .hook(auditHook)
                        .build();
    }

    /**
     * Chat with the hook-instrumented agent. Timing and audit data are collected automatically
     * during execution.
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
     * Get collected timing metrics and the audit log.
     *
     * @return a JSON response containing timing metrics and audit entries
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timing", timingHook.getMetrics());
        result.put("auditLog", auditHook.getAuditLog());
        result.put("auditLogSize", auditHook.getAuditLog().size());
        return ResponseEntity.ok(result);
    }

    /**
     * Reset all timing metrics and clear the audit log.
     *
     * @return a JSON response confirming the reset
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        timingHook.reset();
        auditHook.reset();
        return ResponseEntity.ok(Map.of("status", "Metrics and audit log cleared"));
    }

    /** Simple request body for the chat endpoint. */
    public record ChatRequest(String message) {}
}
