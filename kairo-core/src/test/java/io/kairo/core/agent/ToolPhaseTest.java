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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.IterationSignal;
import io.kairo.api.hook.HookChain;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ToolPhaseTest {

    private ModelProvider modelProvider;
    private HookChain hookChain;
    private GracefulShutdownManager shutdownManager;
    private TokenBudgetManager tokenBudgetManager;
    private AtomicBoolean interrupted;
    private AtomicInteger currentIteration;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        hookChain = new DefaultHookChain();
        shutdownManager = new GracefulShutdownManager();
        tokenBudgetManager = new TokenBudgetManager(200_000, 8_096);
        interrupted = new AtomicBoolean(false);
        currentIteration = new AtomicInteger(0);
        toolExecutor = mock(ToolExecutor.class);
        // executeParallel delegates to individual execute() calls — matches the
        // wiring DefaultToolExecutor uses, lets us stub one tool at a time.
        when(toolExecutor.executeParallel(anyList()))
                .thenAnswer(
                        invocation -> {
                            List<io.kairo.api.tool.ToolInvocation> invocations =
                                    invocation.getArgument(0);
                            return Flux.fromIterable(invocations)
                                    .flatMap(
                                            inv ->
                                                    toolExecutor.execute(
                                                            inv.toolName(), inv.input()));
                        });
    }

    private ReActLoopContext buildContext() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();
        ErrorRecoveryStrategy errorRecovery =
                new ErrorRecoveryStrategy(modelProvider, null, new ModelFallbackManager(List.of()));
        return new ReActLoopContext(
                "agent-1",
                "test-agent",
                config,
                hookChain,
                null,
                toolExecutor,
                errorRecovery,
                tokenBudgetManager,
                shutdownManager,
                null,
                null,
                null,
                null);
    }

    private ToolPhase newPhase(List<Msg> history) {
        ReActLoopContext ctx = buildContext();
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);
        HookDecisionApplier hookDecisions = new HookDecisionApplier(ctx);
        return new ToolPhase(ctx, guards, hookDecisions, history, currentIteration);
    }

    private static Content.ToolUseContent toolCall(
            String id, String name, Map<String, Object> args) {
        return new Content.ToolUseContent(id, name, args);
    }

    // ── existing smoke tests ────────────────────────────────────────────────

    @Test
    void construction_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> newPhase(new ArrayList<>()));
    }

    @Test
    void setCompactionTrigger_null_doesNotThrow() {
        ToolPhase phase = newPhase(new ArrayList<>());
        assertThatNoException().isThrownBy(() -> phase.setCompactionTrigger(null));
    }

    // ── new coverage: execute() happy path + edge cases ─────────────────────

    @Test
    void execute_emptyToolCalls_returnsContinueWithZeroCount() {
        // Empty calls list still goes through the pipeline (history append +
        // signal). The aggregator decides next iteration based on the count.
        ToolPhase phase = newPhase(new ArrayList<>());

        StepVerifier.create(phase.execute(List.of()))
                .assertNext(
                        signal -> {
                            assertThat(signal)
                                    .isInstanceOf(IterationSignal.ContinueAfterTools.class);
                            IterationSignal.ContinueAfterTools cat =
                                    (IterationSignal.ContinueAfterTools) signal;
                            assertThat(cat.toolCallCount()).isZero();
                        })
                .verifyComplete();
    }

    @Test
    void execute_singleSuccessfulCall_returnsContinueWithCountOne() {
        when(toolExecutor.execute(eq("search"), any()))
                .thenReturn(Mono.just(ToolResult.success("tc-1", "found 3 results")));

        ToolPhase phase = newPhase(new ArrayList<>());

        StepVerifier.create(phase.execute(List.of(toolCall("tc-1", "search", Map.of("q", "hi")))))
                .assertNext(
                        signal -> {
                            assertThat(signal)
                                    .isInstanceOf(IterationSignal.ContinueAfterTools.class);
                            assertThat(
                                            ((IterationSignal.ContinueAfterTools) signal)
                                                    .toolCallCount())
                                    .isEqualTo(1);
                        })
                .verifyComplete();
    }

    @Test
    void execute_incrementsTotalToolCallsCounter() {
        when(toolExecutor.execute(eq("read"), any()))
                .thenReturn(Mono.just(ToolResult.success("tc-1", "ok")));

        ToolPhase phase = newPhase(new ArrayList<>());
        assertThat(phase.getTotalToolCalls()).isZero();

        StepVerifier.create(phase.execute(List.of(toolCall("tc-1", "read", Map.of("path", "/a")))))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(phase.getTotalToolCalls()).isEqualTo(1);

        // Second batch of 2 calls → counter advances to 3.
        StepVerifier.create(
                        phase.execute(
                                List.of(
                                        toolCall("tc-2", "read", Map.of("path", "/b")),
                                        toolCall("tc-3", "read", Map.of("path", "/c")))))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(phase.getTotalToolCalls()).isEqualTo(3);
    }

    @Test
    void execute_appendsAggregatedToolResultMsgToHistory() {
        when(toolExecutor.execute(eq("read"), any()))
                .thenReturn(Mono.just(ToolResult.success("tc-1", "file contents")));

        List<Msg> history = new ArrayList<>();
        ToolPhase phase = newPhase(history);

        StepVerifier.create(phase.execute(List.of(toolCall("tc-1", "read", Map.of("path", "/a")))))
                .expectNextCount(1)
                .verifyComplete();

        // History grew by one — the aggregated tool-result message that the
        // model sees on the next reasoning turn.
        assertThat(history).hasSize(1);
    }

    @Test
    void execute_whenInterrupted_propagatesAgentInterruptedException() {
        // Interrupt before invocation — guards.checkCancelled at the head of
        // the pipeline raises AgentInterruptedException through Reactor's
        // error channel (caller's ReActLoop maps it to an Abort signal one
        // layer up; ToolPhase itself just propagates).
        interrupted.set(true);

        ToolPhase phase = newPhase(new ArrayList<>());

        StepVerifier.create(phase.execute(List.of(toolCall("tc-1", "read", Map.of("path", "/a")))))
                .expectError(io.kairo.api.exception.AgentInterruptedException.class)
                .verify();
    }

    @Test
    void execute_toolFailure_doesNotAbortIteration() {
        // A failing tool result is part of normal agent flow — model gets the
        // error in the result msg and decides what to do next. ToolPhase
        // shouldn't escalate to Abort just because one tool failed.
        when(toolExecutor.execute(eq("bash"), any()))
                .thenReturn(Mono.just(ToolResult.error("tc-1", "command not found")));

        ToolPhase phase = newPhase(new ArrayList<>());

        StepVerifier.create(phase.execute(List.of(toolCall("tc-1", "bash", Map.of("cmd", "xx")))))
                .assertNext(
                        signal ->
                                assertThat(signal)
                                        .isInstanceOf(IterationSignal.ContinueAfterTools.class))
                .verifyComplete();
    }

    @Test
    void execute_executorThrowsRuntimeException_wrappedAsToolResultError() {
        // A tool that errors during execution is materialized as a failing
        // ToolResult by the dispatcher — NOT propagated as a Reactor error.
        // The agent loop continues so the next reasoning turn can see the
        // failure message and decide how to recover.
        when(toolExecutor.execute(eq("bash"), any()))
                .thenReturn(Mono.error(new RuntimeException("boom")));

        ToolPhase phase = newPhase(new ArrayList<>());

        StepVerifier.create(phase.execute(List.of(toolCall("tc-1", "bash", Map.of("cmd", "xx")))))
                .assertNext(
                        signal ->
                                assertThat(signal)
                                        .isInstanceOf(IterationSignal.ContinueAfterTools.class))
                .verifyComplete();
    }

    @Test
    void execute_multipleToolCalls_dispatchedInBatch() {
        when(toolExecutor.execute(eq("read"), any()))
                .thenReturn(Mono.just(ToolResult.success("tc-r", "content")));
        when(toolExecutor.execute(eq("grep"), any()))
                .thenReturn(Mono.just(ToolResult.success("tc-g", "match")));

        ToolPhase phase = newPhase(new ArrayList<>());

        StepVerifier.create(
                        phase.execute(
                                List.of(
                                        toolCall("tc-r", "read", Map.of("path", "/a")),
                                        toolCall("tc-g", "grep", Map.of("q", "foo")))))
                .assertNext(
                        signal -> {
                            assertThat(signal)
                                    .isInstanceOf(IterationSignal.ContinueAfterTools.class);
                            assertThat(
                                            ((IterationSignal.ContinueAfterTools) signal)
                                                    .toolCallCount())
                                    .isEqualTo(2);
                        })
                .verifyComplete();
        assertThat(phase.getTotalToolCalls()).isEqualTo(2);
    }
}
