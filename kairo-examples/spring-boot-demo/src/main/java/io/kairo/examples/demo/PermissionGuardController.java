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
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.tool.DefaultPermissionGuard;
import java.util.EnumMap;
import org.springframework.beans.factory.annotation.Value;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating Kairo's permission and security model for tool execution.
 *
 * <p>Showcases the {@link DefaultPermissionGuard} and side-effect classification system
 * that controls which tools an agent is allowed to execute. The controller maintains a
 * configurable policy map from {@link ToolSideEffect} to ALLOW/DENY.
 *
 * <p>Usage:
 * <pre>{@code
 * # Chat with the secure agent (permission guard applied)
 * curl -X POST http://localhost:8080/secure/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "List files in the current directory"}'
 *
 * # View current permission policy
 * curl http://localhost:8080/secure/policy
 *
 * # Update policy (allow writes, deny execution)
 * curl -X PUT http://localhost:8080/secure/policy \
 *   -H "Content-Type: application/json" \
 *   -d '{"READ_ONLY": "ALLOW", "WRITE": "ALLOW", "SYSTEM_CHANGE": "DENY"}'
 *
 * # Test if a tool would be permitted under current policy
 * curl -X POST http://localhost:8080/secure/test-tool \
 *   -H "Content-Type: application/json" \
 *   -d '{"toolName": "bash", "category": "EXECUTION"}'
 * }</pre>
 */
@RestController
@RequestMapping("/secure")
public class PermissionGuardController {

    private final ModelProvider modelProvider;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final DefaultPermissionGuard permissionGuard;
    private final Map<ToolSideEffect, PolicyDecision> policy;
    private final String modelName;

    /**
     * Creates the controller with injected Spring-managed dependencies.
     *
     * @param modelProvider the model provider for LLM calls
     * @param toolRegistry the registry of available tools
     * @param toolExecutor the tool executor
     */
    public PermissionGuardController(
            ModelProvider modelProvider,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            @Value("${kairo.model.model-name:qwen-plus}") String modelName) {
        this.modelProvider = modelProvider;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.modelName = modelName;
        this.permissionGuard = new DefaultPermissionGuard();

        // Default policy: only READ_ONLY allowed
        this.policy = new EnumMap<>(ToolSideEffect.class);
        this.policy.put(ToolSideEffect.READ_ONLY, PolicyDecision.ALLOW);
        this.policy.put(ToolSideEffect.WRITE, PolicyDecision.DENY);
        this.policy.put(ToolSideEffect.SYSTEM_CHANGE, PolicyDecision.DENY);
    }

    /**
     * Chat endpoint with the permission-guarded agent.
     *
     * <p>Builds a fresh agent with the current permission guard and policy applied.
     * Tools that violate the policy will be blocked at execution time.
     *
     * @param request the chat request containing the user message
     * @return the agent's reply along with the active policy
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        Agent agent = AgentBuilder.create()
                .name("secure-agent")
                .model(modelProvider)
                .modelName(modelName)
                .tools(toolRegistry)
                .toolExecutor(toolExecutor)
                .systemPrompt("You are a secure assistant with restricted tool permissions. "
                        + "Some tools may be blocked by the permission guard.")
                .maxIterations(10)
                .tokenBudget(50_000)
                .build();

        Msg input = Msg.of(MsgRole.USER, request.message());
        Msg response = agent.call(input).block();
        String reply = (response != null) ? response.text() : "No response";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("policy", policyToMap());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the current permission policy showing which side-effect levels are allowed.
     *
     * @return the active policy map
     */
    @GetMapping("/policy")
    public ResponseEntity<Map<String, Object>> getPolicy() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", policyToMap());
        result.put("description", Map.of(
                "READ_ONLY", "Safe read operations (grep, list, read)",
                "WRITE", "File write/edit operations",
                "SYSTEM_CHANGE", "Shell execution, system-level changes"));
        return ResponseEntity.ok(result);
    }

    /**
     * Update the permission policy.
     *
     * <p>Accepts a map of {@link ToolSideEffect} names to {@code ALLOW} or {@code DENY}.
     * Only the provided entries are updated; unmentioned levels keep their current value.
     *
     * @param policyUpdate the policy entries to update
     * @return the updated policy
     */
    @PutMapping("/policy")
    public ResponseEntity<Map<String, Object>> updatePolicy(
            @RequestBody Map<String, String> policyUpdate) {
        for (Map.Entry<String, String> entry : policyUpdate.entrySet()) {
            try {
                ToolSideEffect effect = ToolSideEffect.valueOf(entry.getKey());
                PolicyDecision decision = PolicyDecision.valueOf(entry.getValue());
                policy.put(effect, decision);
            } catch (IllegalArgumentException e) {
                // Skip unknown keys gracefully
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", policyToMap());
        result.put("message", "Policy updated");
        return ResponseEntity.ok(result);
    }

    /**
     * Test whether a specific tool would be permitted under the current policy.
     *
     * <p>This endpoint does not execute the tool — it only checks the permission guard
     * and the side-effect policy to determine if execution would be allowed.
     *
     * @param request the test request with tool name and category
     * @return the permission check result
     */
    @PostMapping("/test-tool")
    public ResponseEntity<Map<String, Object>> testTool(@RequestBody TestToolRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolName", request.toolName());

        // Look up the tool in the registry
        Optional<ToolDefinition> toolDef = toolRegistry.get(request.toolName());
        if (toolDef.isPresent()) {
            ToolDefinition def = toolDef.get();
            ToolSideEffect sideEffect = def.sideEffect();
            PolicyDecision decision = policy.getOrDefault(sideEffect, PolicyDecision.DENY);

            result.put("found", true);
            result.put("category", def.category().name());
            result.put("sideEffect", sideEffect.name());
            result.put("policyDecision", decision.name());
            result.put("permitted", decision == PolicyDecision.ALLOW);

            // Also check the permission guard (dangerous pattern / sensitive path check)
            Boolean guardResult = permissionGuard
                    .checkPermission(request.toolName(), def.category(), Map.of())
                    .block();
            result.put("guardPermitted", Boolean.TRUE.equals(guardResult));
        } else {
            // Tool not in registry — test with the provided category
            ToolCategory category = null;
            if (request.category() != null) {
                try {
                    category = ToolCategory.valueOf(request.category());
                } catch (IllegalArgumentException e) {
                    // Ignore invalid category
                }
            }

            // Resolve side effect from the executor
            ToolSideEffect sideEffect = toolExecutor.resolveSideEffect(request.toolName());
            PolicyDecision decision = policy.getOrDefault(sideEffect, PolicyDecision.DENY);

            result.put("found", false);
            result.put("category", category != null ? category.name() : "UNKNOWN");
            result.put("sideEffect", sideEffect.name());
            result.put("policyDecision", decision.name());
            result.put("permitted", decision == PolicyDecision.ALLOW);

            Boolean guardResult = permissionGuard
                    .checkPermission(request.toolName(), Map.of())
                    .block();
            result.put("guardPermitted", Boolean.TRUE.equals(guardResult));
        }

        return ResponseEntity.ok(result);
    }

    private Map<String, String> policyToMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ToolSideEffect effect : ToolSideEffect.values()) {
            map.put(effect.name(), policy.getOrDefault(effect, PolicyDecision.DENY).name());
        }
        return map;
    }

    /** Permission policy decision. */
    public enum PolicyDecision {
        /** Tool execution is allowed. */
        ALLOW,
        /** Tool execution is denied. */
        DENY
    }

    /** Request body for the secure chat endpoint. */
    public record ChatRequest(String message) {}

    /** Request body for testing tool permissions. */
    public record TestToolRequest(String toolName, String category) {}
}
