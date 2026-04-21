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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.hook.HookChain;
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
 * Tests for cooperative cancellation in {@link ReActLoop}.
 *
 * <p>Verifies that the {@code checkCancelled()} guard fires at each reactive chain boundary: before
 * tool execution, before recursion, and before flush/compaction.
 */
class CooperativeCancellationTest {

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

    private ReActLoop createLoop(int maxIterations) {
        AgentConfig config =
                AgentConfig.builder()
                        .name("cancel-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(maxIterations)
                        .tokenBudget(200_000)
                        .build();

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-cancel",
                        "cancel-agent",
                        config,
                        hookChain,
                        null,
                        toolExecutor,
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

        return new ReActLoop(
                ctx, interrupted, currentIteration, totalTokensUsed, () -> modelConfig);
    }

    private ModelResponse toolCallResponse(
            String toolId, String toolName, Map<String, Object> input) {
        return new ModelResponse(
                "resp-tool",
                List.of(new Content.ToolUseContent(toolId, toolName, input)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "test-model");
    }

    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-text",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    // ===== 1. Interrupt before tool execution =====

    @Test
    void interruptBeforeToolExecution_exitsWithAgentInterruptedException() {
        // Model returns a tool call; we set interrupted before tools execute
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            // Set interrupted after model responds but before tool execution
                            interrupted.set(true);
                            return Mono.just(
                                    toolCallResponse("tc-1", "search", Map.of("q", "hello")));
                        });

        ReActLoop loop = createLoop(10);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "search hello")));

        StepVerifier.create(loop.runLoop())
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("interrupted at iteration"))
                .verify();

        // Tool executor should never be called — cancellation fires before tool execution
        verify(toolExecutor, never()).execute(any(), any());
    }

    // ===== 2. Interrupt between tool result and next iteration =====

    @Test
    void interruptBetweenToolResultAndRecursion_exitsCleanly() {
        // First model call returns tool call; tool executes; then we interrupt before recursion
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(toolCallResponse("tc-1", "fetch", Map.of()));
                            }
                            return Mono.just(textResponse("Should not reach here."));
                        });

        when(toolExecutor.execute(eq("fetch"), any()))
                .thenAnswer(
                        inv -> {
                            // Set interrupted after tool completes — before next runLoop()
                            interrupted.set(true);
                            return Mono.just(
                                    new ToolResult("tc-1", "fetched data", false, Map.of()));
                        });

        ReActLoop loop = createLoop(10);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "fetch data")));

        StepVerifier.create(loop.runLoop())
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("interrupted at iteration"))
                .verify();

        // Model should only be called once (the first tool-call response)
        assertEquals(1, callCount.get());
    }

    // ===== 3. No interrupt — loop continues normally (regression) =====

    @Test
    void noInterrupt_loopCompletesNormally() {
        // Standard tool-call cycle: model returns tool call, then final text
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(
                                        toolCallResponse("tc-1", "echo", Map.of("msg", "hi")));
                            }
                            return Mono.just(textResponse("Echo complete."));
                        });

        when(toolExecutor.execute(eq("echo"), any()))
                .thenReturn(Mono.just(new ToolResult("tc-1", "hi", false, Map.of())));

        ReActLoop loop = createLoop(10);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "echo hi")));

        StepVerifier.create(loop.runLoop())
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Echo complete."));
                        })
                .verifyComplete();

        // Both model calls and tool execution should have happened
        assertEquals(2, callCount.get());
        verify(toolExecutor).execute(eq("echo"), any());
    }

    // ===== 4. Interrupt before flush — compaction/flush is skipped =====

    @Test
    void interruptBeforeFlush_flushIsSkipped() {
        // Model returns tool call; tool executes; we set interrupted before flush/compaction.
        // When a CompactionTrigger is set, the checkCancelled() guard fires before
        // checkAndCompact is reached, so the agent exits cleanly.
        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                return Mono.just(toolCallResponse("tc-1", "work", Map.of()));
                            }
                            return Mono.just(textResponse("Should not reach."));
                        });

        when(toolExecutor.execute(eq("work"), any()))
                .thenAnswer(
                        inv -> {
                            // Set interrupted — the checkCancelled before compaction/recursion
                            // catches it
                            interrupted.set(true);
                            return Mono.just(new ToolResult("tc-1", "done", false, Map.of()));
                        });

        ReActLoop loop = createLoop(10);
        // Use a real CompactionTrigger with null contextManager — checkAndCompact
        // would be a no-op, but the checkCancelled() guard should fire first anyway.
        loop.setCompactionTrigger(new CompactionTrigger(null, loop));
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "do work")));

        StepVerifier.create(loop.runLoop())
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("interrupted at iteration"))
                .verify();

        // Model should only be called once (the tool-call response)
        assertEquals(1, callCount.get());
    }

    @Test
    void interruptBetweenSequentialTools_secondToolIsNotExecuted() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(
                        Mono.just(
                                new ModelResponse(
                                        "resp-multi",
                                        List.of(
                                                new Content.ToolUseContent(
                                                        "tc-1", "first", Map.of()),
                                                new Content.ToolUseContent(
                                                        "tc-2", "second", Map.of())),
                                        new ModelResponse.Usage(10, 20, 0, 0),
                                        ModelResponse.StopReason.TOOL_USE,
                                        "test-model")));

        AtomicReference<String> lastTool = new AtomicReference<>();
        when(toolExecutor.execute(any(), any()))
                .thenAnswer(
                        inv -> {
                            String toolName = inv.getArgument(0);
                            lastTool.set(toolName);
                            if ("first".equals(toolName)) {
                                interrupted.set(true);
                            }
                            return Mono.just(
                                    new ToolResult("tc-" + toolName, "ok", false, Map.of()));
                        });

        ReActLoop loop = createLoop(10);
        loop.injectMessages(List.of(Msg.of(MsgRole.USER, "run both tools")));

        StepVerifier.create(loop.runLoop())
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("interrupted at iteration"))
                .verify();

        verify(toolExecutor, times(1)).execute(any(), any());
        assertEquals("first", lastTool.get());
    }
}
