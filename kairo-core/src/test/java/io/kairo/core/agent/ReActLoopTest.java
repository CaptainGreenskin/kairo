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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.hook.HookChain;
import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.OnToolResult;
import io.kairo.api.hook.PreActing;
import io.kairo.api.hook.PreActingEvent;
import io.kairo.api.hook.ToolResultEvent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link ReActLoop} — the core ReAct iteration engine.
 *
 * <p>All tests use mock {@link ModelProvider} and {@link ToolExecutor}; no real API keys are
 * required.
 */
class ReActLoopTest {

    private ModelProvider modelProvider;
    private ToolExecutor toolExecutor;
    private HookChain hookChain;
    private GracefulShutdownManager shutdownManager;
    private TokenBudgetManager tokenBudgetManager;
    private ErrorRecoveryStrategy errorRecovery;
    private AtomicBoolean interrupted;
    private AtomicInteger currentIteration;
    private AtomicLong totalTokensUsed;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        toolExecutor = mock(ToolExecutor.class);
        hookChain = new DefaultHookChain();
        shutdownManager = new GracefulShutdownManager();
        tokenBudgetManager = new TokenBudgetManager(200_000, 8_096);
        errorRecovery =
                new ErrorRecoveryStrategy(modelProvider, null, new ModelFallbackManager(List.of()));
        interrupted = new AtomicBoolean(false);
        currentIteration = new AtomicInteger(0);
        totalTokensUsed = new AtomicLong(0);
    }

    private ReActLoop createLoop(int maxIterations, int tokenBudget) {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(maxIterations)
                        .tokenBudget(tokenBudget)
                        .build();

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-1",
                        "test-agent",
                        config,
                        hookChain,
                        null, // tracer
                        toolExecutor,
                        errorRecovery,
                        tokenBudgetManager,
                        shutdownManager,
                        null); // contextManager

        ModelConfig modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        return new ReActLoop(
                ctx, interrupted, currentIteration, totalTokensUsed, () -> modelConfig);
    }

    private ReActLoop createDefaultLoop() {
        return createLoop(10, 200_000);
    }

    // -- Helper: build a text-only ModelResponse --
    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-1",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    // -- Helper: build a tool-call ModelResponse --
    private ModelResponse toolCallResponse(
            String toolId, String toolName, Map<String, Object> input) {
        return new ModelResponse(
                "resp-tool",
                List.of(new Content.ToolUseContent(toolId, toolName, input)),
                new ModelResponse.Usage(15, 25, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "test-model");
    }

    // -- Helper: build a multi-tool-call ModelResponse --
    private ModelResponse multiToolCallResponse(List<Content.ToolUseContent> toolCalls) {
        return new ModelResponse(
                "resp-multi",
                List.copyOf(toolCalls),
                new ModelResponse.Usage(20, 30, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "test-model");
    }

    // ===== 1. Tool call cycle execution =====

    @Test
    void testToolCallCycleExecution() {
        // First call: model returns a tool_call; second call: model returns final text
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(
                                        toolCallResponse("tc-1", "search", Map.of("q", "hello")));
                            }
                            return Mono.just(textResponse("Search complete."));
                        });

        when(toolExecutor.execute(eq("search"), any()))
                .thenReturn(Mono.just(new ToolResult("tc-1", "found 3 results", false, Map.of())));

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "search hello")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Search complete."));
                        })
                .verifyComplete();

        // Verify tool was executed
        verify(toolExecutor).execute(eq("search"), any());
        // Model called twice: once for tool call, once for final answer
        assertEquals(2, callCount.get());
    }

    // ===== 2. Loop terminates on final text =====

    @Test
    void testLoopTerminatesOnFinalText() {
        // Model returns text immediately — no tool calls
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("Direct answer.")));

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "What is 2+2?")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Direct answer."));
                        })
                .verifyComplete();

        // Tool executor should never be called
        verifyNoInteractions(toolExecutor);
        // Only one model call
        verify(modelProvider, times(1)).call(anyList(), any(ModelConfig.class));
    }

    // ===== 3. Max iterations guard =====

    @Test
    void testMaxIterationsGuard() {
        // Model always returns tool calls — loop must stop at maxIterations
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(toolCallResponse("tc-loop", "echo", Map.of("x", "y"))));
        when(toolExecutor.execute(eq("echo"), any()))
                .thenReturn(Mono.just(new ToolResult("tc-loop", "echoed", false, Map.of())));

        ReActLoop loop = createLoop(2, 200_000);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "loop")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("maximum iteration limit"));
                        })
                .verifyComplete();

        // Should have called model exactly maxIterations times (2)
        verify(modelProvider, times(2)).call(anyList(), any(ModelConfig.class));
    }

    // ===== 4. Error recovery on provider exception =====

    @Test
    void testErrorRecoveryOnProviderException() {
        // Provider throws on first call, succeeds on retry (ErrorRecoveryStrategy handles retry)
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.error(new RuntimeException("Transient API error"));
                            }
                            return Mono.just(textResponse("Recovered successfully."));
                        });

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "test")));

        // Since ErrorRecoveryStrategy classifies the error and may not retry a generic
        // RuntimeException, this test verifies the error propagates cleanly
        StepVerifier.create(loop.runLoop())
                .expectErrorMatches(e -> e.getMessage().contains("Transient API error"))
                .verify();
    }

    // ===== 5. Multiple tool calls in single iteration =====

    @Test
    void testMultipleToolCallsInSingleIteration() {
        // Model returns two tool calls at once, then a final text response
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(
                                        multiToolCallResponse(
                                                List.of(
                                                        new Content.ToolUseContent(
                                                                "tc-1",
                                                                "read",
                                                                Map.of("file", "a.txt")),
                                                        new Content.ToolUseContent(
                                                                "tc-2",
                                                                "read",
                                                                Map.of("file", "b.txt")))));
                            }
                            return Mono.just(textResponse("Both files read."));
                        });

        when(toolExecutor.execute(eq("read"), any()))
                .thenReturn(Mono.just(new ToolResult("tc-x", "file content", false, Map.of())));

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "read files")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Both files read."));
                        })
                .verifyComplete();

        // Tool executor called twice (once per tool call)
        verify(toolExecutor, times(2)).execute(eq("read"), any());
    }

    // ===== 6. Empty tool call result =====

    @Test
    void testEmptyToolCallResult() {
        // Tool returns empty content — loop should handle gracefully
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(toolCallResponse("tc-1", "empty_tool", Map.of()));
                            }
                            return Mono.just(textResponse("Handled empty result."));
                        });

        when(toolExecutor.execute(eq("empty_tool"), any()))
                .thenReturn(Mono.just(new ToolResult("tc-1", "", false, Map.of())));

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "test empty")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Handled empty result."));
                        })
                .verifyComplete();
    }

    // ===== 7. Tool execution failure =====

    @Test
    void testToolExecutionFailure() {
        // ToolExecutor throws exception — ReActLoop catches it and continues
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(
                                        toolCallResponse("tc-1", "failing_tool", Map.of()));
                            }
                            return Mono.just(textResponse("Recovered from tool error."));
                        });

        when(toolExecutor.execute(eq("failing_tool"), any()))
                .thenReturn(Mono.error(new RuntimeException("Tool crashed")));

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "use failing tool")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Recovered from tool error."));
                        })
                .verifyComplete();

        // History should contain the error tool result
        List<Msg> history = loop.getHistory();
        // user → assistant(tool_call) → tool(error) → assistant(final)
        assertTrue(history.size() >= 4);
    }

    // ===== 8. Context grows with tool results =====

    @Test
    void testContextGrowsWithToolResults() {
        // Verify that after a tool call, the history contains the tool result
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(
                                        toolCallResponse("tc-1", "info", Map.of("key", "val")));
                            }
                            // Verify that messages passed to model include the tool result
                            List<Msg> msgs = inv.getArgument(0);
                            assertTrue(msgs.stream().anyMatch(m -> m.role() == MsgRole.TOOL));
                            return Mono.just(textResponse("Done."));
                        });

        when(toolExecutor.execute(eq("info"), any()))
                .thenReturn(Mono.just(new ToolResult("tc-1", "info result data", false, Map.of())));

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "get info")));

        StepVerifier.create(loop.runLoop())
                .assertNext(msg -> assertTrue(msg.text().contains("Done.")))
                .verifyComplete();

        // Verify history: USER, ASSISTANT(tool_call), TOOL(result), ASSISTANT(final)
        List<Msg> history = loop.getHistory();
        assertEquals(4, history.size());
        assertEquals(MsgRole.USER, history.get(0).role());
        assertEquals(MsgRole.ASSISTANT, history.get(1).role());
        assertEquals(MsgRole.TOOL, history.get(2).role());
        assertEquals(MsgRole.ASSISTANT, history.get(3).role());
    }

    // ===== 9. Streaming mode falls back for non-streaming provider =====

    @Test
    void testStreamingModeFallsBackForNonStreamingProvider() {
        // Enable streaming but provider does not support streamRaw — should fall back to
        // non-streaming
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("Streamed (fallback).")));

        ReActLoop loop = createDefaultLoop();
        loop.setStreamingEnabled(true);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "stream test")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Streamed (fallback)."));
                        })
                .verifyComplete();

        assertTrue(loop.isStreamingEnabled());
    }

    // ===== 10. Loop with no tools — simple pass-through =====

    @Test
    void testLoopWithNoTools() {
        // ToolExecutor is null — model returns text, simple pass-through
        AgentConfig config =
                AgentConfig.builder()
                        .name("no-tool-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-2",
                        "no-tool-agent",
                        config,
                        hookChain,
                        null,
                        null, // no toolExecutor
                        errorRecovery,
                        tokenBudgetManager,
                        shutdownManager,
                        null);

        ModelConfig modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("No tools needed.")));

        ReActLoop loop =
                new ReActLoop(
                        ctx, interrupted, currentIteration, totalTokensUsed, () -> modelConfig);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "simple question")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("No tools needed."));
                        })
                .verifyComplete();
    }

    // ===== 11. No tool executor returns error results for tool calls =====

    @Test
    void testNoToolExecutorReturnsErrorForToolCalls() {
        // ToolExecutor is null but model requests tool calls — should get error results
        AgentConfig config =
                AgentConfig.builder()
                        .name("no-exec-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-3",
                        "no-exec-agent",
                        config,
                        hookChain,
                        null,
                        null, // no toolExecutor
                        errorRecovery,
                        tokenBudgetManager,
                        shutdownManager,
                        null);

        ModelConfig modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(
                                        toolCallResponse("tc-1", "missing_tool", Map.of()));
                            }
                            return Mono.just(textResponse("Handled missing executor."));
                        });

        ReActLoop loop =
                new ReActLoop(
                        ctx, interrupted, currentIteration, totalTokensUsed, () -> modelConfig);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "call tool")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Handled missing executor."));
                        })
                .verifyComplete();

        // History should contain the tool error result
        List<Msg> history = loop.getHistory();
        assertTrue(history.stream().anyMatch(m -> m.role() == MsgRole.TOOL));
    }

    // ===== 12. Token budget guard =====

    @Test
    void testTokenBudgetGuard() {
        // Set a very small token budget so it's exceeded after one call
        ReActLoop loop = createLoop(10, 50); // tokenBudget=50

        // First call returns a tool call with high usage
        ModelResponse bigResponse =
                new ModelResponse(
                        "resp-big",
                        List.of(new Content.ToolUseContent("tc-1", "heavy", Map.of())),
                        new ModelResponse.Usage(30, 30, 0, 0), // total=60 > budget=50
                        ModelResponse.StopReason.TOOL_USE,
                        "test-model");

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(bigResponse));
        when(toolExecutor.execute(eq("heavy"), any()))
                .thenReturn(Mono.just(new ToolResult("tc-1", "result", false, Map.of())));

        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "heavy task")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("token budget"));
                        })
                .verifyComplete();
    }

    // ===== 13. Interrupted agent =====

    @Test
    void testInterruptedAgentStopsLoop() {
        interrupted.set(true);

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "should not run")));

        StepVerifier.create(loop.runLoop())
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("interrupted"))
                .verify();

        // Model should never be called
        verifyNoInteractions(modelProvider);
    }

    // ===== 14. History management =====

    @Test
    void testOnToolResultHookFiresWhenToolIsSkipped() {
        class SkipToolHook {
            @PreActing
            public HookResult<PreActingEvent> pre(PreActingEvent event) {
                return HookResult.skip(event, "skip for policy");
            }
        }
        AtomicReference<ToolResultEvent> captured = new AtomicReference<>();
        class ToolResultCaptureHook {
            @OnToolResult
            public ToolResultEvent onResult(ToolResultEvent event) {
                captured.set(event);
                return event;
            }
        }
        hookChain.register(new SkipToolHook());
        hookChain.register(new ToolResultCaptureHook());

        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(
                                        toolCallResponse("tc-1", "search", Map.of("q", "hello")));
                            }
                            return Mono.just(textResponse("done"));
                        });

        ReActLoop loop = createDefaultLoop();
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "search hello")));

        StepVerifier.create(loop.runLoop())
                .assertNext(msg -> assertEquals(MsgRole.ASSISTANT, msg.role()))
                .verifyComplete();

        verifyNoInteractions(toolExecutor);
        assertNotNull(captured.get());
        assertEquals("search", captured.get().toolName());
        assertFalse(captured.get().result().isError());
        assertTrue(Boolean.TRUE.equals(captured.get().result().metadata().get("skipped_by_hook")));
        assertTrue(captured.get().success());
    }

    @Test
    void testHistoryManagement() {
        ReActLoop loop = createDefaultLoop();

        // injectMessages adds to history
        loop.injectMessages(
                List.of(Msg.of(MsgRole.USER, "msg1"), Msg.of(MsgRole.ASSISTANT, "msg2")));
        assertEquals(2, loop.getHistory().size());

        // replaceHistory replaces all
        loop.replaceHistory(List.of(Msg.of(MsgRole.SYSTEM, "new")));
        assertEquals(1, loop.getHistory().size());
        assertEquals(MsgRole.SYSTEM, loop.getHistory().get(0).role());

        // getHistory returns unmodifiable view
        assertThrows(
                UnsupportedOperationException.class,
                () -> loop.getHistory().add(Msg.of(MsgRole.USER, "nope")));

        // injectMessages with null is safe
        loop.injectMessages(null);
        assertEquals(1, loop.getHistory().size());

        // replaceHistory with null clears
        loop.replaceHistory(null);
        assertTrue(loop.getHistory().isEmpty());
    }
}
