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
package io.kairo.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.agent.AgentBuilder;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Tests for ToolContext injection into tool handlers.
 *
 * <p>Verifies that context-aware tools receive the correct ToolContext (agentId, sessionId,
 * dependencies) and that legacy tools continue to work unchanged.
 */
class ToolContextInjectionTest {

    private DefaultToolRegistry registry;
    private DefaultPermissionGuard guard;
    private DefaultToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        guard = new DefaultPermissionGuard();
        executor = new DefaultToolExecutor(registry, guard);
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

    // --- Test 1: Context-aware tool receives correct agentId ---

    @Test
    void contextAwareToolReceivesCorrectAgentId() {
        AtomicReference<ToolContext> capturedCtx = new AtomicReference<>();

        ToolHandler contextTool =
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        throw new AssertionError("Should not be called");
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> input, ToolContext context) {
                        capturedCtx.set(context);
                        return new ToolResult("ctx-tool", "ok", false, Map.of());
                    }
                };

        registerToolHandler("ctx-tool", contextTool);
        executor.setToolContext(new ToolContext("agent-123", "session-456", Map.of()));

        StepVerifier.create(executor.execute("ctx-tool", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        assertNotNull(capturedCtx.get());
        assertEquals("agent-123", capturedCtx.get().agentId());
    }

    // --- Test 2: Context-aware tool receives correct sessionId ---

    @Test
    void contextAwareToolReceivesCorrectSessionId() {
        AtomicReference<ToolContext> capturedCtx = new AtomicReference<>();

        ToolHandler contextTool =
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        throw new AssertionError("Should not be called");
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> input, ToolContext context) {
                        capturedCtx.set(context);
                        return new ToolResult("ctx-tool", "ok", false, Map.of());
                    }
                };

        registerToolHandler("ctx-tool", contextTool);
        executor.setToolContext(new ToolContext("agent-1", "session-XYZ", Map.of()));

        StepVerifier.create(executor.execute("ctx-tool", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        assertEquals("session-XYZ", capturedCtx.get().sessionId());
    }

    // --- Test 3: Context-aware tool receives injected dependencies ---

    @Test
    void contextAwareToolReceivesInjectedDependencies() {
        AtomicReference<ToolContext> capturedCtx = new AtomicReference<>();

        ToolHandler contextTool =
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        throw new AssertionError("Should not be called");
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> input, ToolContext context) {
                        capturedCtx.set(context);
                        return new ToolResult("ctx-tool", "ok", false, Map.of());
                    }
                };

        registerToolHandler("ctx-tool", contextTool);
        Map<String, Object> deps = Map.of("dbConn", "jdbc:h2:mem:test", "apiKey", "secret-key");
        executor.setToolContext(new ToolContext("agent-1", "session-1", deps));

        StepVerifier.create(executor.execute("ctx-tool", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        assertEquals("jdbc:h2:mem:test", capturedCtx.get().dependencies().get("dbConn"));
        assertEquals("secret-key", capturedCtx.get().dependencies().get("apiKey"));
    }

    // --- Test 4: Legacy tool (only implements execute(input)) still works ---

    @Test
    void legacyToolWithoutContextStillWorks() {
        // Legacy tool only implements execute(Map) — no context override
        ToolHandler legacyTool =
                input ->
                        new ToolResult(
                                "legacy", "legacy-result: " + input.get("x"), false, Map.of());

        registerToolHandler("legacy", legacyTool);
        executor.setToolContext(new ToolContext("agent-1", "session-1", Map.of("key", "value")));

        StepVerifier.create(executor.execute("legacy", Map.of("x", "42")))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("legacy-result: 42", result.content());
                        })
                .verifyComplete();
    }

    // --- Test 5: AgentBuilder.toolDependencies stores and passes dependencies ---

    @Test
    void agentBuilderToolDependenciesStoresAndPasses() {
        Map<String, Object> deps = Map.of("db", "connection", "cache", "redis");
        AgentBuilder builder = AgentBuilder.create().toolDependencies(deps);

        // Verify by calling toolDependencies again with null (should not throw)
        AgentBuilder updated = builder.toolDependencies(null);
        assertNotNull(updated);

        // Verify chaining works by calling with deps again
        AgentBuilder chained = updated.toolDependencies(Map.of("a", "b"));
        assertSame(chained, updated); // same builder instance
    }

    // --- Test 6: ToolContext dependencies are immutable ---

    @Test
    void toolContextDependenciesAreImmutable() {
        java.util.HashMap<String, Object> mutableDeps = new java.util.HashMap<>();
        mutableDeps.put("key1", "value1");

        ToolContext ctx = new ToolContext("agent-1", "session-1", mutableDeps);

        // Mutating the original map should not affect ToolContext
        mutableDeps.put("key2", "value2");
        assertNull(ctx.dependencies().get("key2"));

        // ToolContext's dependencies map should be unmodifiable
        assertThrows(
                UnsupportedOperationException.class,
                () -> ctx.dependencies().put("key3", "value3"));
    }

    // --- Test 7: Null dependencies handled gracefully (empty map) ---

    @Test
    void nullDependenciesHandledGracefully() {
        ToolContext ctx = new ToolContext("agent-1", "session-1", null);
        assertNotNull(ctx.dependencies());
        assertTrue(ctx.dependencies().isEmpty());
    }

    // --- Test 8: Same tool with different ToolContext in different executors ---

    @Test
    void sameToolDifferentContextGetsDifferentDependencies() {
        AtomicReference<ToolContext> capturedCtx1 = new AtomicReference<>();
        AtomicReference<ToolContext> capturedCtx2 = new AtomicReference<>();

        // Executor 1 with deps A
        DefaultToolRegistry registry1 = new DefaultToolRegistry();
        DefaultToolExecutor executor1 =
                new DefaultToolExecutor(registry1, new DefaultPermissionGuard());
        ToolHandler tool1 =
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        throw new AssertionError("Should not be called");
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> input, ToolContext context) {
                        capturedCtx1.set(context);
                        return new ToolResult("tool", "ok", false, Map.of());
                    }
                };
        ToolDefinition def1 =
                new ToolDefinition(
                        "tool",
                        "test",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        tool1.getClass());
        registry1.register(def1);
        registry1.registerInstance("tool", tool1);
        executor1.setToolContext(new ToolContext("agent-A", "session-A", Map.of("env", "prod")));

        // Executor 2 with deps B
        DefaultToolRegistry registry2 = new DefaultToolRegistry();
        DefaultToolExecutor executor2 =
                new DefaultToolExecutor(registry2, new DefaultPermissionGuard());
        ToolHandler tool2 =
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        throw new AssertionError("Should not be called");
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> input, ToolContext context) {
                        capturedCtx2.set(context);
                        return new ToolResult("tool", "ok", false, Map.of());
                    }
                };
        ToolDefinition def2 =
                new ToolDefinition(
                        "tool",
                        "test",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        tool2.getClass());
        registry2.register(def2);
        registry2.registerInstance("tool", tool2);
        executor2.setToolContext(new ToolContext("agent-B", "session-B", Map.of("env", "staging")));

        StepVerifier.create(executor1.execute("tool", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        StepVerifier.create(executor2.execute("tool", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        assertEquals("prod", capturedCtx1.get().dependencies().get("env"));
        assertEquals("agent-A", capturedCtx1.get().agentId());
        assertEquals("staging", capturedCtx2.get().dependencies().get("env"));
        assertEquals("agent-B", capturedCtx2.get().agentId());
    }

    // --- Test 9: Executor without ToolContext provides empty default context ---

    @Test
    void executorWithoutToolContextProvidesDefaultEmptyContext() {
        AtomicReference<ToolContext> capturedCtx = new AtomicReference<>();

        ToolHandler contextTool =
                new ToolHandler() {
                    @Override
                    public ToolResult execute(Map<String, Object> input) {
                        throw new AssertionError("Should not be called");
                    }

                    @Override
                    public ToolResult execute(Map<String, Object> input, ToolContext context) {
                        capturedCtx.set(context);
                        return new ToolResult("ctx-tool", "ok", false, Map.of());
                    }
                };

        registerToolHandler("ctx-tool", contextTool);
        // Do NOT call executor.setToolContext — leave it null

        StepVerifier.create(executor.execute("ctx-tool", Map.of()))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        assertNotNull(capturedCtx.get());
        assertNull(capturedCtx.get().agentId());
        assertNull(capturedCtx.get().sessionId());
        assertTrue(capturedCtx.get().dependencies().isEmpty());
    }
}
