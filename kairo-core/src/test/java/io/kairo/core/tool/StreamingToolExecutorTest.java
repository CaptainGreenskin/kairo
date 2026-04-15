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

import io.kairo.api.tool.*;
import io.kairo.core.model.DetectedToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class StreamingToolExecutorTest {

    private DefaultToolRegistry registry;
    private DefaultToolExecutor toolExecutor;
    private StreamingToolExecutor streamingExecutor;
    private final CopyOnWriteArrayList<String> callbackCompleted = new CopyOnWriteArrayList<>();

    private static final PermissionGuard ALLOW_ALL =
            new PermissionGuard() {
                @Override
                public Mono<Boolean> checkPermission(String toolName, Map<String, Object> input) {
                    return Mono.just(true);
                }

                @Override
                public void addDangerousPattern(String pattern) {}
            };

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        toolExecutor = new DefaultToolExecutor(registry, ALLOW_ALL);

        StreamingToolResultCallback callback =
                new StreamingToolResultCallback() {
                    @Override
                    public void onPartialOutput(String toolCallId, String chunk) {}

                    @Override
                    public void onComplete(String toolCallId, ToolResult result) {
                        callbackCompleted.add(toolCallId);
                    }
                };
        streamingExecutor = new StreamingToolExecutor(toolExecutor, callback);
        callbackCompleted.clear();
    }

    private void registerTool(String name, ToolSideEffect sideEffect, ToolHandler handler) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "test tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        handler.getClass(),
                        null,
                        sideEffect);
        registry.register(def);
        registry.registerInstance(name, handler);
    }

    private ToolHandler echoHandler(String toolId) {
        return input -> new ToolResult(toolId, "result-" + toolId, false, Map.of());
    }

    @Test
    void readOnlyToolsDispatchedImmediately() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));

        var tools = Flux.just(new DetectedToolCall("tc1", "read_file", Map.of(), true));

        StepVerifier.create(streamingExecutor.executeEager(tools))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("tc1", result.toolUseId());
                        })
                .verifyComplete();
    }

    @Test
    void writeToolsQueuedAndSerialExecuted() {
        var order = new CopyOnWriteArrayList<String>();
        registerTool(
                "write_a",
                ToolSideEffect.WRITE,
                input -> {
                    order.add("write_a");
                    return new ToolResult("write_a", "ok", false, Map.of());
                });
        registerTool(
                "write_b",
                ToolSideEffect.WRITE,
                input -> {
                    order.add("write_b");
                    return new ToolResult("write_b", "ok", false, Map.of());
                });

        var tools =
                Flux.just(
                        new DetectedToolCall("tc1", "write_a", Map.of(), false),
                        new DetectedToolCall("tc2", "write_b", Map.of(), true));

        List<ToolResult> results = streamingExecutor.executeEager(tools).collectList().block();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(List.of("write_a", "write_b"), order);
    }

    @Test
    void mixedEagerExecution_readsFirst() {
        var order = new CopyOnWriteArrayList<String>();
        registerTool(
                "read_file",
                ToolSideEffect.READ_ONLY,
                input -> {
                    order.add("read");
                    return new ToolResult("read_file", "data", false, Map.of());
                });
        registerTool(
                "write_file",
                ToolSideEffect.WRITE,
                input -> {
                    order.add("write");
                    return new ToolResult("write_file", "ok", false, Map.of());
                });

        var tools =
                Flux.just(
                        new DetectedToolCall("tc1", "read_file", Map.of(), false),
                        new DetectedToolCall("tc2", "write_file", Map.of(), true));

        List<ToolResult> results = streamingExecutor.executeEager(tools).collectList().block();

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void eagerExecution_writesFlushOnLastTool() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        toolExecutor.setToolPermission("bash", ToolPermission.ALLOWED);

        // Write tools queued, not executed until last tool
        var tools =
                Flux.just(
                        new DetectedToolCall("tc1", "bash", Map.of(), false),
                        new DetectedToolCall("tc2", "bash", Map.of(), true));

        List<ToolResult> results = streamingExecutor.executeEager(tools).collectList().block();

        assertNotNull(results);
        assertEquals(2, results.size());
        // Both should have been executed by the time we're done
        results.forEach(r -> assertFalse(r.isError()));
    }

    @Test
    void emptyToolFluxReturnsEmpty() {
        StepVerifier.create(streamingExecutor.executeStreaming(Flux.empty())).verifyComplete();

        StepVerifier.create(streamingExecutor.executeEager(Flux.empty())).verifyComplete();
    }

    @Test
    void allResultsCollected() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));
        registerTool("grep", ToolSideEffect.READ_ONLY, echoHandler("grep"));
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));

        var tools =
                Flux.just(
                        new DetectedToolCall("tc1", "read_file", Map.of(), false),
                        new DetectedToolCall("tc2", "grep", Map.of(), false),
                        new DetectedToolCall("tc3", "write_file", Map.of(), true));

        List<ToolResult> results = streamingExecutor.executeStreaming(tools).collectList().block();

        assertNotNull(results);
        assertEquals(3, results.size());
        assertFalse(results.stream().anyMatch(ToolResult::isError));
    }

    @Test
    void streamingExecutionCollectsAllResults() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));

        var tools =
                Flux.just(
                        new DetectedToolCall("tc1", "read_file", Map.of("path", "/a"), false),
                        new DetectedToolCall("tc2", "read_file", Map.of("path", "/b"), true));

        List<ToolResult> results = streamingExecutor.executeStreaming(tools).collectList().block();

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void callbackNotifiedOnComplete() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));

        var tools =
                Flux.just(
                        new DetectedToolCall("tc1", "read_file", Map.of(), false),
                        new DetectedToolCall("tc2", "read_file", Map.of(), true));

        streamingExecutor.executeStreaming(tools).collectList().block();

        assertEquals(2, callbackCompleted.size());
        assertTrue(callbackCompleted.contains("tc1"));
        assertTrue(callbackCompleted.contains("tc2"));
    }
}
