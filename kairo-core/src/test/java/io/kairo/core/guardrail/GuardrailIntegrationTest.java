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
package io.kairo.core.guardrail;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.guardrail.*;
import io.kairo.api.tool.*;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests verifying guardrail evaluation at tool boundaries within {@link
 * DefaultToolExecutor}.
 */
class GuardrailIntegrationTest {

    private DefaultToolRegistry registry;
    private DefaultPermissionGuard guard;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        guard = new DefaultPermissionGuard();
    }

    private void registerToolHandler(String name, ToolHandler handler) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "test tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        handler.getClass());
        registry.register(def);
        registry.registerInstance(name, handler);
    }

    // ---- PRE_TOOL DENY blocks tool execution ----

    @Test
    void preToolDeny_blocksToolExecution() {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        registerToolHandler(
                "dangerous-tool",
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        handlerCalled.set(true);
                        return new ToolResult("id", "done", false, Map.of());
                    }
                });

        GuardrailPolicy denyPolicy =
                new GuardrailPolicy() {
                    @Override
                    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                        if (context.phase() == GuardrailPhase.PRE_TOOL) {
                            return Mono.just(
                                    GuardrailDecision.deny("tool is blocked", "deny-policy"));
                        }
                        return Mono.just(GuardrailDecision.allow("deny-policy"));
                    }
                };

        GuardrailChain chain = new DefaultGuardrailChain(List.of(denyPolicy));
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, guard, null, null, 3, chain);

        StepVerifier.create(executor.execute("dangerous-tool", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("Guardrail denied"));
                            assertTrue(result.content().contains("tool is blocked"));
                        })
                .verifyComplete();

        assertFalse(handlerCalled.get(), "Tool handler should NOT have been called");
    }

    // ---- POST_TOOL MODIFY alters returned ToolResult ----

    @Test
    void postToolModify_altersToolResult() {
        registerToolHandler(
                "echo-tool",
                (ToolHandler) input -> new ToolResult("id", "original-output", false, Map.of()));

        GuardrailPolicy modifyPolicy =
                new GuardrailPolicy() {
                    @Override
                    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                        if (context.phase() == GuardrailPhase.POST_TOOL
                                && context.payload()
                                        instanceof GuardrailPayload.ToolOutput toolOutput) {
                            ToolResult original = toolOutput.result();
                            ToolResult redacted =
                                    new ToolResult(
                                            original.toolUseId(),
                                            "[REDACTED]",
                                            original.isError(),
                                            original.metadata());
                            return Mono.just(
                                    GuardrailDecision.modify(
                                            new GuardrailPayload.ToolOutput(
                                                    toolOutput.toolName(), redacted),
                                            "redacted output",
                                            "redact-policy"));
                        }
                        return Mono.just(GuardrailDecision.allow("redact-policy"));
                    }
                };

        GuardrailChain chain = new DefaultGuardrailChain(List.of(modifyPolicy));
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, guard, null, null, 3, chain);

        StepVerifier.create(executor.execute("echo-tool", Map.of()))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("[REDACTED]", result.content());
                        })
                .verifyComplete();
    }

    // ---- PRE_TOOL MODIFY alters input args ----

    @Test
    void preToolModify_altersInputArgs() {
        registerToolHandler(
                "arg-tool",
                (ToolHandler)
                        input ->
                                new ToolResult(
                                        "id", "received:" + input.get("key"), false, Map.of()));

        GuardrailPolicy modifyInputPolicy =
                new GuardrailPolicy() {
                    @Override
                    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                        if (context.phase() == GuardrailPhase.PRE_TOOL
                                && context.payload()
                                        instanceof GuardrailPayload.ToolInput toolInput) {
                            return Mono.just(
                                    GuardrailDecision.modify(
                                            new GuardrailPayload.ToolInput(
                                                    toolInput.toolName(),
                                                    Map.of("key", "modified-value")),
                                            "modified input",
                                            "input-modify-policy"));
                        }
                        return Mono.just(GuardrailDecision.allow("input-modify-policy"));
                    }
                };

        GuardrailChain chain = new DefaultGuardrailChain(List.of(modifyInputPolicy));
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, guard, null, null, 3, chain);

        StepVerifier.create(executor.execute("arg-tool", Map.of("key", "original")))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("received:modified-value", result.content());
                        })
                .verifyComplete();
    }

    // ---- POST_TOOL WARN allows response to proceed ----

    @Test
    void postToolWarn_allowsResponseToProceed() {
        registerToolHandler(
                "warn-tool",
                (ToolHandler) input -> new ToolResult("id", "normal-output", false, Map.of()));

        GuardrailPolicy warnPolicy =
                new GuardrailPolicy() {
                    @Override
                    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                        if (context.phase() == GuardrailPhase.POST_TOOL) {
                            return Mono.just(
                                    GuardrailDecision.warn("suspicious output", "warn-policy"));
                        }
                        return Mono.just(GuardrailDecision.allow("warn-policy"));
                    }
                };

        GuardrailChain chain = new DefaultGuardrailChain(List.of(warnPolicy));
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, guard, null, null, 3, chain);

        StepVerifier.create(executor.execute("warn-tool", Map.of()))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("normal-output", result.content());
                        })
                .verifyComplete();
    }

    // ---- No guardrail chain (null) → pipeline works as before ----

    @Test
    void nullGuardrailChain_pipelineWorksNormally() {
        registerToolHandler(
                "simple-tool",
                (ToolHandler) input -> new ToolResult("id", "hello", false, Map.of()));

        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, guard, null, null, 3, null);

        StepVerifier.create(executor.execute("simple-tool", Map.of()))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("hello", result.content());
                        })
                .verifyComplete();
    }

    // ---- Guardrail with empty policies → pipeline works as before ----

    @Test
    void emptyPolicies_pipelineWorksNormally() {
        registerToolHandler(
                "simple-tool",
                (ToolHandler) input -> new ToolResult("id", "hello", false, Map.of()));

        GuardrailChain chain = new DefaultGuardrailChain(List.of());
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, guard, null, null, 3, chain);

        StepVerifier.create(executor.execute("simple-tool", Map.of()))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("hello", result.content());
                        })
                .verifyComplete();
    }

    // ---- POST_TOOL DENY blocks result ----

    @Test
    void postToolDeny_blocksResult() {
        registerToolHandler(
                "leaky-tool",
                (ToolHandler) input -> new ToolResult("id", "secret-data", false, Map.of()));

        GuardrailPolicy postDenyPolicy =
                new GuardrailPolicy() {
                    @Override
                    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
                        if (context.phase() == GuardrailPhase.POST_TOOL) {
                            return Mono.just(
                                    GuardrailDecision.deny(
                                            "output contains sensitive data", "post-deny-policy"));
                        }
                        return Mono.just(GuardrailDecision.allow("post-deny-policy"));
                    }
                };

        GuardrailChain chain = new DefaultGuardrailChain(List.of(postDenyPolicy));
        DefaultToolExecutor executor =
                new DefaultToolExecutor(registry, guard, null, null, 3, chain);

        StepVerifier.create(executor.execute("leaky-tool", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("Guardrail denied"));
                            assertTrue(result.content().contains("sensitive data"));
                        })
                .verifyComplete();
    }
}
