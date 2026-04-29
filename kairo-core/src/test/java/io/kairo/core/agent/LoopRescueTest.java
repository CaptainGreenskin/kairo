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
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.hook.HookChain;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for loop rescue injection in {@link ToolPhase}. */
class LoopRescueTest {

    private ModelProvider modelProvider;
    private HookChain hookChain;
    private GracefulShutdownManager shutdownManager;
    private TokenBudgetManager tokenBudgetManager;
    private AtomicBoolean interrupted;
    private AtomicInteger currentIteration;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        hookChain = new DefaultHookChain();
        shutdownManager = new GracefulShutdownManager();
        tokenBudgetManager = new TokenBudgetManager(200_000, 8_096);
        interrupted = new AtomicBoolean(false);
        currentIteration = new AtomicInteger(0);
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
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
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
                null);
    }

    private static List<Content.ToolUseContent> identicalToolCalls() {
        return List.of(new Content.ToolUseContent("id-1", "read_file", Map.of("path", "test.txt")));
    }

    private ToolPhase buildToolPhaseWithImmediateHardStop(List<Msg> conversationHistory) {
        ReActLoopContext ctx = buildContext();
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);
        HookDecisionApplier hookDecisions = new HookDecisionApplier(ctx);
        LoopDetector loopDetector =
                new LoopDetector(100, 1, 100, 200, Duration.ofMinutes(10), 1000);
        return new ToolPhase(
                ctx, guards, hookDecisions, conversationHistory, loopDetector, currentIteration);
    }

    @Test
    void firstLoopDetection_injectsRescuePromptAndContinues() {
        List<Msg> history = new ArrayList<>();
        ToolPhase phase = buildToolPhaseWithImmediateHardStop(history);

        List<Content.ToolUseContent> calls = identicalToolCalls();
        int sizeBefore = history.size();

        Mono<Msg> result =
                phase.executeAndContinue(calls, () -> Mono.just(Msg.of(MsgRole.ASSISTANT, "done")));

        StepVerifier.create(result)
                .assertNext(
                        msg -> {
                            assertEquals(sizeBefore + 1, history.size());
                            Msg rescueMsg = history.get(sizeBefore);
                            assertEquals(MsgRole.USER, rescueMsg.role());
                            Content firstContent = rescueMsg.contents().get(0);
                            assertTrue(firstContent.toString().contains("Loop Rescue"));
                            Content responseContent = msg.contents().get(0);
                            assertTrue(responseContent.toString().contains("done"));
                        })
                .verifyComplete();
    }

    @Test
    void secondLoopDetection_throwsLoopDetectionException() {
        List<Msg> history = new ArrayList<>();
        ToolPhase phase = buildToolPhaseWithImmediateHardStop(history);

        List<Content.ToolUseContent> calls = identicalToolCalls();

        // First detection: injects rescue, continues
        Mono<Msg> firstResult =
                phase.executeAndContinue(
                        calls, () -> Mono.just(Msg.of(MsgRole.ASSISTANT, "retry")));
        Msg firstMsg = firstResult.block();
        assertNotNull(firstMsg);

        // Second detection: should throw
        Mono<Msg> secondResult =
                phase.executeAndContinue(
                        calls, () -> Mono.just(Msg.of(MsgRole.ASSISTANT, "retry2")));
        StepVerifier.create(secondResult).expectError(LoopDetectionException.class).verify();
    }

    @Test
    void rescuePrompt_hasExpectedContent() {
        List<Msg> history = new ArrayList<>();
        ToolPhase phase = buildToolPhaseWithImmediateHardStop(history);

        List<Content.ToolUseContent> calls = identicalToolCalls();
        phase.executeAndContinue(calls, () -> Mono.just(Msg.of(MsgRole.ASSISTANT, "done"))).block();

        List<Msg> rescueMsgs =
                history.stream()
                        .filter(m -> m.role() == MsgRole.USER)
                        .filter(
                                m ->
                                        m.contents().stream()
                                                .anyMatch(
                                                        c -> c.toString().contains("Loop Rescue")))
                        .toList();

        assertEquals(1, rescueMsgs.size());
        Content content = rescueMsgs.get(0).contents().get(0);
        String text = content.toString();
        assertTrue(text.contains("repeating the same approach"));
        assertTrue(text.contains("different approach"));
    }
}
