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
package io.kairo.core.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.tool.*;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.ModelCapabilityRegistry;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.model.ToolDescriptionAdapter;
import io.kairo.core.session.SessionManager;
import io.kairo.core.session.SessionSnapshot;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.core.tool.ToolHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cross-module integration tests exercising full end-to-end flows across kairo-api, kairo-core, and
 * kairo-tools boundaries.
 *
 * <p>Uses stub tools and mock model providers to deterministically exercise the real agent loop,
 * tool executor, session persistence, permission system, and error recovery.
 */
class AgentIntegrationTest {

    // ================================
    //  Stub Tools (avoid circular dep on kairo-tools)
    // ================================

    /** A simple read-only tool that returns a canned response. */
    @Tool(
            name = "stub_read",
            description =
                    "Read file contents from the filesystem. Returns the content of the specified"
                            + " file path as a string.")
    public static class StubReadTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            String path = (String) input.getOrDefault("path", "/tmp/test.txt");
            return new ToolResult("stub_read", "Content of " + path, false, Map.of("path", path));
        }
    }

    /** A write tool that creates a real file. */
    @Tool(
            name = "stub_write",
            description =
                    "Create or overwrite a file with the given content. Automatically creates"
                            + " parent directories if they do not exist.",
            sideEffect = ToolSideEffect.WRITE)
    public static class StubWriteTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            String path = (String) input.get("path");
            String content = (String) input.get("content");
            if (path == null || content == null) {
                return new ToolResult("stub_write", "Missing path or content", true, Map.of());
            }
            try {
                Path file = Path.of(path);
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, content);
                return new ToolResult(
                        "stub_write",
                        "Wrote " + content.length() + " chars to " + path,
                        false,
                        Map.of("path", path));
            } catch (Exception e) {
                return new ToolResult("stub_write", "Error: " + e.getMessage(), true, Map.of());
            }
        }
    }

    /** A stub bash tool with SYSTEM_CHANGE side-effect. */
    @Tool(
            name = "stub_bash",
            description =
                    "Execute a shell command and return its output. Use for running programs,"
                            + " installing packages, or system operations.",
            category = ToolCategory.EXECUTION,
            sideEffect = ToolSideEffect.SYSTEM_CHANGE)
    public static class StubBashTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            String command = (String) input.getOrDefault("command", "");
            return new ToolResult(
                    "stub_bash",
                    "Executed: " + command + "\nOutput: ok",
                    false,
                    Map.of("command", command));
        }
    }

    /** A second read-only tool for partition testing. */
    @Tool(name = "stub_glob", description = "Find files by glob pattern.")
    public static class StubGlobTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            String pattern = (String) input.getOrDefault("pattern", "*");
            return new ToolResult(
                    "stub_glob", "Found: file1.txt, file2.txt", false, Map.of("pattern", pattern));
        }
    }

    // ================================
    //  Scripted Mock Model Provider
    // ================================

    /**
     * A mock model provider that returns pre-scripted responses per call. Each call pops the next
     * response from a queue. If the queue has a RuntimeException, it throws instead.
     */
    public static class ScriptedModelProvider implements ModelProvider {
        private final List<Object> scriptedResponses = new ArrayList<>();
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public String name() {
            return "scripted-mock";
        }

        /** Add a ModelResponse to the script. */
        ScriptedModelProvider thenReturn(ModelResponse response) {
            scriptedResponses.add(response);
            return this;
        }

        /** Add an error to the script (will be thrown on that call). */
        ScriptedModelProvider thenThrow(RuntimeException error) {
            scriptedResponses.add(error);
            return this;
        }

        int getCallCount() {
            return callCount.get();
        }

        @Override
        public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
            int idx = callCount.getAndIncrement();
            if (idx >= scriptedResponses.size()) {
                // Default: return a final text response
                return Mono.just(textResponse("Default response (no more scripted responses)"));
            }
            Object response = scriptedResponses.get(idx);
            if (response instanceof RuntimeException ex) {
                return Mono.error(ex);
            }
            return Mono.just((ModelResponse) response);
        }

        @Override
        public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
            return call(messages, config).flux();
        }
    }

    // ================================
    //  Helper: build common responses
    // ================================

    static ModelResponse toolCallResponse(String toolName, Map<String, Object> input) {
        String toolId = "toolu_" + UUID.randomUUID().toString().substring(0, 12);
        return new ModelResponse(
                "msg_mock",
                List.of(new Content.ToolUseContent(toolId, toolName, input)),
                new ModelResponse.Usage(100, 50, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "mock-model");
    }

    static ModelResponse textResponse(String text) {
        return new ModelResponse(
                "msg_mock",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(100, 50, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "mock-model");
    }

    static ModelResponse multiToolCallResponse(List<Map.Entry<String, Map<String, Object>>> tools) {
        List<Content> contents = new ArrayList<>();
        for (var entry : tools) {
            String toolId = "toolu_" + UUID.randomUUID().toString().substring(0, 12);
            contents.add(new Content.ToolUseContent(toolId, entry.getKey(), entry.getValue()));
        }
        return new ModelResponse(
                "msg_mock",
                contents,
                new ModelResponse.Usage(100, 50, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "mock-model");
    }

    // ================================
    //  Helper: build registry with stub tools
    // ================================

    static DefaultToolRegistry buildRegistry(Class<? extends ToolHandler>... toolClasses) {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        for (var cls : toolClasses) {
            registry.registerTool(cls);
        }
        return registry;
    }

    // ================================
    //  Test 1: Full ReAct Loop
    // ================================

    @Test
    void fullReActLoop_mockProvider_completesTask(@TempDir Path tempDir) {
        // Arrange: provider returns write tool call, then final text
        Path targetFile = tempDir.resolve("hello.txt");
        ScriptedModelProvider provider =
                new ScriptedModelProvider()
                        .thenReturn(
                                toolCallResponse(
                                        "stub_write",
                                        Map.of(
                                                "path",
                                                targetFile.toString(),
                                                "content",
                                                "Hello from Kairo!")))
                        .thenReturn(textResponse("Done! File created successfully."));

        DefaultToolRegistry registry = buildRegistry(StubWriteTool.class, StubReadTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        Agent agent =
                AgentBuilder.create()
                        .name("test-react-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .systemPrompt("You are a test assistant.")
                        .maxIterations(10)
                        .build();

        // Act
        Msg result = agent.call(MsgBuilder.user("Create hello.txt")).block(Duration.ofSeconds(30));

        // Assert
        assertNotNull(result);
        assertTrue(result.text().contains("Done!"));
        assertTrue(Files.exists(targetFile), "File should have been created by the write tool");
        assertEquals(2, provider.getCallCount(), "Should have made 2 LLM calls");
        assertEquals(AgentState.COMPLETED, agent.state());
    }

    // ================================
    //  Test 2: Tool Partition — Mixed Read/Write
    // ================================

    @Test
    void toolPartition_mixedBatch_readsParallelWritesSerial(@TempDir Path tempDir) {
        // Arrange: create a batch with 2 READ_ONLY + 1 WRITE invocation
        DefaultToolRegistry registry =
                buildRegistry(StubReadTool.class, StubGlobTool.class, StubWriteTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        Path targetFile = tempDir.resolve("partition-test.txt");
        List<ToolInvocation> invocations =
                List.of(
                        new ToolInvocation("stub_glob", Map.of("pattern", "*.java")),
                        new ToolInvocation("stub_read", Map.of("path", "/tmp/test.txt")),
                        new ToolInvocation(
                                "stub_write",
                                Map.of("path", targetFile.toString(), "content", "partitioned")));

        // Act
        List<ToolResult> results = executor.executePartitioned(invocations).collectList().block();

        // Assert
        assertNotNull(results);
        assertEquals(3, results.size());
        // Results should be in original invocation order
        assertFalse(results.get(0).isError(), "glob should succeed");
        assertTrue(results.get(0).content().contains("Found:"));
        assertFalse(results.get(1).isError(), "read should succeed");
        assertTrue(results.get(1).content().contains("Content of"));
        assertFalse(results.get(2).isError(), "write should succeed");
        assertTrue(results.get(2).content().contains("Wrote"));
        // Verify side-effect classification
        assertEquals(ToolSideEffect.READ_ONLY, executor.resolveSideEffect("stub_glob"));
        assertEquals(ToolSideEffect.READ_ONLY, executor.resolveSideEffect("stub_read"));
        assertEquals(ToolSideEffect.WRITE, executor.resolveSideEffect("stub_write"));
    }

    // ================================
    //  Test 3: Error Recovery — Prompt Too Long
    // ================================

    @Test
    void errorRecovery_promptTooLong_compactsAndRetries() {
        // Arrange: build up enough history (>10 non-system messages) so truncation
        // can actually reduce the list. Script 6 tool call rounds (each adding
        // assistant + tool_result = 2 msgs) = 12 non-system msgs + 1 user = 13.
        // Then throw prompt-too-long -> truncation keeps last 10 -> retry -> success.
        ScriptedModelProvider provider = new ScriptedModelProvider();
        for (int i = 0; i < 6; i++) {
            provider.thenReturn(
                    toolCallResponse("stub_read", Map.of("path", "/tmp/file" + i + ".txt")));
        }
        provider.thenThrow(new RuntimeException("prompt is too long"));
        provider.thenReturn(textResponse("Recovered after compaction!"));

        DefaultToolRegistry registry = buildRegistry(StubReadTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        Agent agent =
                AgentBuilder.create()
                        .name("compaction-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .systemPrompt("You are a test assistant.")
                        .maxIterations(20)
                        .build();

        // Act
        Msg result =
                agent.call(MsgBuilder.user("Process a very long input"))
                        .block(Duration.ofSeconds(30));

        // Assert
        assertNotNull(result);
        assertTrue(result.text().contains("Recovered after compaction!"));
        assertTrue(provider.getCallCount() >= 8, "Should have completed 6 tools + error + retry");
    }

    // ================================
    //  Test 4: Error Recovery — Rate Limited
    // ================================

    @Test
    void errorRecovery_rateLimited_waitsAndRetries() {
        // Arrange: provider throws rate limit error on first call with "retry after 1 seconds"
        ScriptedModelProvider provider =
                new ScriptedModelProvider()
                        .thenThrow(new RuntimeException("429 rate limited. Retry after 1 seconds"))
                        .thenReturn(textResponse("Success after rate limit!"));

        Agent agent =
                AgentBuilder.create()
                        .name("rate-limit-agent")
                        .model(provider)
                        .systemPrompt("Test")
                        .maxIterations(10)
                        .build();

        // Act
        long start = System.currentTimeMillis();
        Msg result =
                agent.call(MsgBuilder.user("Rate limited request")).block(Duration.ofSeconds(30));
        long elapsed = System.currentTimeMillis() - start;

        // Assert
        assertNotNull(result);
        assertTrue(result.text().contains("Success after rate limit!"));
        assertEquals(2, provider.getCallCount());
        // Should have waited approximately 1 second for rate limit backoff
        assertTrue(
                elapsed >= 800, "Should have waited ~1 second, but only waited " + elapsed + "ms");
    }

    // ================================
    //  Test 5: Error Recovery — Model Fallback
    // ================================

    @Test
    void errorRecovery_serverError_fallbackManagerAdvances() {
        // Test the ModelFallbackManager directly (it's used by DefaultReActAgent internally)
        ModelFallbackManager manager =
                new ModelFallbackManager(List.of("claude-3-haiku", "claude-3-opus"));

        // Initially has fallback
        assertTrue(manager.hasFallback());
        assertEquals("claude-sonnet-4", manager.currentModel("claude-sonnet-4"));

        // Advance through fallbacks
        assertEquals("claude-3-haiku", manager.nextFallback());
        assertTrue(manager.hasFallback());
        assertEquals("claude-3-haiku", manager.currentModel("claude-sonnet-4"));

        assertEquals("claude-3-opus", manager.nextFallback());
        assertFalse(manager.hasFallback());
        assertEquals("claude-3-opus", manager.currentModel("claude-sonnet-4"));

        // Exhausted
        assertNull(manager.nextFallback());

        // Reset brings it back
        manager.reset();
        assertTrue(manager.hasFallback());
        assertEquals("claude-sonnet-4", manager.currentModel("claude-sonnet-4"));
    }

    // ================================
    //  Test 6: Plan Mode Enforcement
    // ================================

    @Test
    void planMode_writeToolBlocked_readToolAllowed() {
        // Arrange
        DefaultToolRegistry registry =
                buildRegistry(StubReadTool.class, StubWriteTool.class, StubBashTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // Enter plan mode
        executor.setPlanMode(true);
        assertTrue(executor.isPlanMode());

        // Read tool should succeed (READ_ONLY)
        ToolResult readResult =
                executor.execute("stub_read", Map.of("path", "/tmp/test.txt"))
                        .block(Duration.ofSeconds(5));
        assertNotNull(readResult);
        assertFalse(readResult.isError(), "READ_ONLY tool should work in plan mode");

        // Write tool should be blocked
        ToolResult writeResult =
                executor.execute("stub_write", Map.of("path", "/tmp/x.txt", "content", "test"))
                        .block(Duration.ofSeconds(5));
        assertNotNull(writeResult);
        assertTrue(writeResult.isError(), "WRITE tool should be blocked in plan mode");
        assertTrue(writeResult.content().contains("blocked in Plan Mode"));

        // Bash tool should be blocked (SYSTEM_CHANGE)
        ToolResult bashResult =
                executor.execute("stub_bash", Map.of("command", "echo hi"))
                        .block(Duration.ofSeconds(5));
        assertNotNull(bashResult);
        assertTrue(bashResult.isError(), "SYSTEM_CHANGE tool should be blocked in plan mode");

        // Exit plan mode — write should now succeed
        executor.setPlanMode(false);
        assertFalse(executor.isPlanMode());

        ToolResult writeAfter =
                executor.execute("stub_write", Map.of("path", "/tmp/x.txt", "content", "test"))
                        .block(Duration.ofSeconds(5));
        assertNotNull(writeAfter);
        // writeAfter may error due to path, but should NOT be a plan mode violation
        assertFalse(
                writeAfter.content().contains("Plan Mode"),
                "Should not be plan mode error after exiting plan mode");
    }

    // ================================
    //  Test 7: Session Persistence Round-Trip
    // ================================

    @Test
    void sessionPersistence_saveAndLoad(@TempDir Path tempDir) {
        // Arrange
        SessionManager sessionManager = new SessionManager(tempDir);
        String sessionId = "test-session-" + UUID.randomUUID();

        List<Map<String, Object>> messages =
                List.of(
                        Map.of("role", "user", "content", "Hello"),
                        Map.of("role", "assistant", "content", "Hi there!"),
                        Map.of("role", "user", "content", "Write a file"));

        Map<String, Object> agentState =
                Map.of("planMode", false, "iteration", 3, "model", "claude-sonnet-4");

        SessionSnapshot original =
                new SessionSnapshot(sessionId, Instant.now(), 3, messages, agentState);

        // Act: save
        sessionManager.saveSession(sessionId, original).block(Duration.ofSeconds(5));

        // Act: load
        SessionSnapshot loaded = sessionManager.loadSession(sessionId).block(Duration.ofSeconds(5));

        // Assert
        assertNotNull(loaded);
        assertEquals(sessionId, loaded.sessionId());
        assertEquals(3, loaded.turnCount());
        assertEquals(3, loaded.messages().size());
        assertEquals("user", loaded.messages().get(0).get("role"));
        assertEquals("Hello", loaded.messages().get(0).get("content"));
        assertEquals("assistant", loaded.messages().get(1).get("role"));
        assertEquals(false, loaded.agentState().get("planMode"));
        assertEquals(3, loaded.agentState().get("iteration"));
    }

    // ================================
    //  Test 8: HITL Approval — Deny Tool
    // ================================

    @Test
    void hitlApproval_denyBashTool_returnsError() {
        // Arrange: handler that always denies
        UserApprovalHandler denyHandler =
                request -> Mono.just(ApprovalResult.denied("Not allowed in test"));

        DefaultToolRegistry registry = buildRegistry(StubBashTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setApprovalHandler(denyHandler);
        // Set SYSTEM_CHANGE permission to ASK (so it triggers approval flow)
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.ASK);

        // Act: try to execute through the partitioned path (uses approval flow)
        List<ToolResult> results =
                executor.executePartitioned(
                                List.of(
                                        new ToolInvocation(
                                                "stub_bash", Map.of("command", "echo hello"))))
                        .collectList()
                        .block(Duration.ofSeconds(5));

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isError(), "Tool should have been denied");
        assertTrue(results.get(0).content().contains("denied by user"));
    }

    // ================================
    //  Test 9: HITL Approval — Allow Tool
    // ================================

    @Test
    void hitlApproval_approveBashTool_executes() {
        // Arrange: handler that always approves
        UserApprovalHandler approveHandler = request -> Mono.just(ApprovalResult.allow());

        DefaultToolRegistry registry = buildRegistry(StubBashTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setApprovalHandler(approveHandler);
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.ASK);

        // Act
        List<ToolResult> results =
                executor.executePartitioned(
                                List.of(
                                        new ToolInvocation(
                                                "stub_bash", Map.of("command", "echo hello"))))
                        .collectList()
                        .block(Duration.ofSeconds(5));

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertFalse(results.get(0).isError(), "Tool should have been approved and executed");
        assertTrue(results.get(0).content().contains("Executed: echo hello"));
    }

    // ================================
    //  Test 10: Model Capability → Tool Verbosity
    // ================================

    @Test
    void modelCapability_affectsToolVerbosity() {
        // Look up capabilities
        var haikuCap = ModelCapabilityRegistry.lookup("claude-3-5-haiku");
        var opusCap = ModelCapabilityRegistry.lookup("claude-3-opus");

        assertEquals(ToolVerbosity.CONCISE, haikuCap.toolVerbosity());
        assertEquals(ToolVerbosity.VERBOSE, opusCap.toolVerbosity());

        // Create tool definitions to adapt
        DefaultToolRegistry registry = buildRegistry(StubWriteTool.class, StubReadTool.class);
        List<ToolDefinition> tools = registry.getAll();

        ToolDescriptionAdapter adapter = new ToolDescriptionAdapter();

        // Adapt for Haiku (CONCISE)
        List<ToolDefinition> conciseTools = adapter.adaptForModel(tools, ToolVerbosity.CONCISE);
        assertNotNull(conciseTools);
        for (ToolDefinition tool : conciseTools) {
            // Concise descriptions should be shorter (truncated to first sentence or 100 chars)
            assertTrue(
                    tool.description().length() <= 101
                            || tool.description().endsWith("...")
                            || tool.description().endsWith("."),
                    "Concise tool description should be short: " + tool.description());
        }

        // Adapt for Opus (VERBOSE)
        List<ToolDefinition> verboseTools = adapter.adaptForModel(tools, ToolVerbosity.VERBOSE);
        assertNotNull(verboseTools);
        for (ToolDefinition tool : verboseTools) {
            // Verbose descriptions should have usage hints appended
            assertTrue(
                    tool.description().contains("Use this tool when"),
                    "Verbose tool should have usage hint: " + tool.description());
        }

        // STANDARD should return unchanged
        List<ToolDefinition> standardTools = adapter.adaptForModel(tools, ToolVerbosity.STANDARD);
        assertSame(tools, standardTools, "STANDARD should return original list");
    }

    // ================================
    //  Test 11: Full Agent with Tool + Error Recovery
    // ================================

    @Test
    void fullAgent_serverErrorThenSuccess_recoversGracefully() {
        // Arrange: provider fails with server error on first call, succeeds on retry
        ScriptedModelProvider provider =
                new ScriptedModelProvider()
                        .thenThrow(new RuntimeException("500 internal server error"))
                        .thenReturn(textResponse("Recovered from server error!"));

        Agent agent =
                AgentBuilder.create()
                        .name("recovery-agent")
                        .model(provider)
                        .systemPrompt("Test")
                        .maxIterations(10)
                        .build();

        // Act
        Msg result =
                agent.call(MsgBuilder.user("Test server error recovery"))
                        .block(Duration.ofSeconds(30));

        // Assert
        assertNotNull(result);
        assertTrue(result.text().contains("Recovered from server error!"));
        assertTrue(provider.getCallCount() >= 2);
    }

    // ================================
    //  Test 12: HITL — No Handler Configured (ASK defaults to deny)
    // ================================

    @Test
    void hitlApproval_noHandler_defaultsToDeny() {
        // Arrange: no approval handler set, but permission is ASK
        DefaultToolRegistry registry = buildRegistry(StubBashTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        // No handler set — should deny for safety
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.ASK);

        // Act
        List<ToolResult> results =
                executor.executePartitioned(
                                List.of(
                                        new ToolInvocation(
                                                "stub_bash", Map.of("command", "echo test"))))
                        .collectList()
                        .block(Duration.ofSeconds(5));

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isError());
        assertTrue(
                results.get(0).content().contains("requires approval but no handler configured"));
    }

    // ================================
    //  Test 13: Session Delete and List
    // ================================

    @Test
    void sessionManager_deleteAndList(@TempDir Path tempDir) {
        SessionManager mgr = new SessionManager(tempDir);
        String sid = "sess-del-test";

        // Save a session
        SessionSnapshot snap =
                new SessionSnapshot(
                        sid,
                        Instant.now(),
                        1,
                        List.of(Map.of("role", "user", "content", "hi")),
                        Map.of());
        mgr.saveSession(sid, snap).block(Duration.ofSeconds(5));

        // Verify it exists
        var loaded = mgr.loadSession(sid).block(Duration.ofSeconds(5));
        assertNotNull(loaded);

        // Delete
        Boolean deleted = mgr.deleteSession(sid).block(Duration.ofSeconds(5));
        assertTrue(deleted);

        // Verify it's gone
        var afterDelete = mgr.loadSession(sid).block(Duration.ofSeconds(5));
        assertNull(afterDelete);
    }

    // ================================
    //  Test 14: Multi-iteration ReAct with Read then Write
    // ================================

    @Test
    void fullReActLoop_readThenWrite_multipleIterations(@TempDir Path tempDir) {
        Path targetFile = tempDir.resolve("output.txt");

        // Scripted: iteration 1 reads, iteration 2 writes, iteration 3 final answer
        ScriptedModelProvider provider =
                new ScriptedModelProvider()
                        .thenReturn(toolCallResponse("stub_read", Map.of("path", "/tmp/test.txt")))
                        .thenReturn(
                                toolCallResponse(
                                        "stub_write",
                                        Map.of(
                                                "path",
                                                targetFile.toString(),
                                                "content",
                                                "Generated output")))
                        .thenReturn(textResponse("All done! Read input and wrote output."));

        DefaultToolRegistry registry = buildRegistry(StubReadTool.class, StubWriteTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        Agent agent =
                AgentBuilder.create()
                        .name("multi-iter-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .systemPrompt("Test multi-iteration")
                        .maxIterations(10)
                        .build();

        // Act
        Msg result =
                agent.call(MsgBuilder.user("Read and write files")).block(Duration.ofSeconds(30));

        // Assert
        assertNotNull(result);
        assertTrue(result.text().contains("All done!"));
        assertEquals(3, provider.getCallCount(), "Should have 3 LLM calls: read, write, final");
        assertTrue(Files.exists(targetFile), "Output file should exist");
        assertEquals(AgentState.COMPLETED, agent.state());

        // Verify conversation history
        DefaultReActAgent reactAgent = (DefaultReActAgent) agent;
        List<Msg> history = reactAgent.conversationHistory();
        // History: user input + assistant(tool_use) + tool_result + assistant(tool_use) +
        // tool_result + assistant(final)
        assertTrue(history.size() >= 6, "Should have at least 6 messages in history");
    }

    // ================================
    //  Test 15: Agent Max Iterations Guard
    // ================================

    @Test
    void agentMaxIterations_stopsAtLimit() {
        // Arrange: provider always returns tool calls (infinite loop)
        ScriptedModelProvider provider = new ScriptedModelProvider();
        // Add 10 tool call responses
        for (int i = 0; i < 10; i++) {
            provider.thenReturn(
                    toolCallResponse("stub_read", Map.of("path", "/tmp/file" + i + ".txt")));
        }

        DefaultToolRegistry registry = buildRegistry(StubReadTool.class);
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        Agent agent =
                AgentBuilder.create()
                        .name("bounded-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .systemPrompt("Test")
                        .maxIterations(3) // Limit to 3 iterations
                        .build();

        // Act
        Msg result =
                agent.call(MsgBuilder.user("Infinite tool loop")).block(Duration.ofSeconds(30));

        // Assert
        assertNotNull(result);
        assertTrue(
                result.text().contains("maximum iteration limit"),
                "Should mention hitting iteration limit: " + result.text());
        // Provider should have been called at most 3 times (one per iteration)
        assertTrue(
                provider.getCallCount() <= 4,
                "Should not exceed max iterations: " + provider.getCallCount());
    }
}
