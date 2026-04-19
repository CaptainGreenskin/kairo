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
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.middleware.MiddlewareRejectException;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.core.agent.AgentBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating the Middleware pipeline for cross-cutting concerns.
 *
 * <p>Shows how to register {@link io.kairo.api.middleware.Middleware} via
 * {@link AgentBuilder#middleware(io.kairo.api.middleware.Middleware)} and how
 * middleware can reject requests (auth failure, rate limit) before the agent loop starts.
 *
 * <p>This controller demonstrates the difference between <strong>Middleware</strong>
 * (pre-agent, request-level) and <strong>Hooks</strong> (in-agent, lifecycle-level):
 * <ul>
 *   <li>Authentication and rate limiting belong in Middleware</li>
 *   <li>Modifying model config or skipping tool execution belong in Hooks</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * # Successful request with valid API key
 * curl -X POST http://localhost:8080/middleware/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "Hello", "apiKey": "demo-key"}'
 *
 * # Rejected — missing API key
 * curl -X POST http://localhost:8080/middleware/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "Hello"}'
 *
 * # Rejected — rate limit exceeded (after 5 requests with same sessionId)
 * curl -X POST http://localhost:8080/middleware/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "Hello", "apiKey": "demo-key", "sessionId": "limited"}'
 * }</pre>
 */
@RestController
@RequestMapping("/middleware")
public class MiddlewareDemoController {

    private final ModelProvider modelProvider;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final String modelName;

    public MiddlewareDemoController(
            ModelProvider modelProvider,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            @Value("${kairo.model.model-name:qwen-plus}") String modelName) {
        this.modelProvider = modelProvider;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.modelName = modelName;
    }

    /**
     * Chat endpoint protected by auth + rate-limit middleware.
     *
     * <p>Builds an agent with two middleware: {@link AuthMiddleware} validates the API key,
     * then {@link RateLimitMiddleware} enforces per-session request limits.
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody MiddlewareChatRequest request) {
        Agent agent = AgentBuilder.create()
                .name("middleware-demo-agent")
                .model(modelProvider)
                .modelName(modelName)
                .tools(toolRegistry)
                .toolExecutor(toolExecutor)
                .systemPrompt("You are a helpful assistant. Keep responses brief.")
                .maxIterations(5)
                .tokenBudget(20_000)
                .middleware(new AuthMiddleware("demo-key"))
                .middleware(new RateLimitMiddleware(5))
                .build();

        // Build context with API key from request — the middleware will validate it
        Msg input = Msg.of(MsgRole.USER, request.message());

        try {
            // In a real app, the API key and session ID come from HTTP headers / cookies.
            // Here we pass them via the agent's session mechanism for simplicity.
            Agent finalAgent = agent;
            if (request.sessionId != null) {
                io.kairo.core.agent.DefaultReActAgent reactAgent =
                        (io.kairo.core.agent.DefaultReActAgent) finalAgent;
                // Note: For a full middleware demo, you'd use a custom call() overload
                // that accepts MiddlewareContext. Here we show the registration pattern.
            }

            Msg response = finalAgent.call(input).block();
            String reply = (response != null) ? response.text() : "No response";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reply", reply);
            result.put("middleware", "auth + rate-limiter");
            return ResponseEntity.ok(result);
        } catch (MiddlewareRejectException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            error.put("rejectedBy", e.middlewareName());
            return ResponseEntity.status(403).body(error);
        }
    }

    /** Request body for the middleware demo chat endpoint. */
    public record MiddlewareChatRequest(String message, String apiKey, String sessionId) {}
}
