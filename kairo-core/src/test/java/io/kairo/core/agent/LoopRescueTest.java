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
import io.kairo.api.agent.IterationSignal;
import io.kairo.api.cost.NoopCostTracker;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for loop rescue injection in {@link ReActLoop}'s dispatcher preamble. */
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
                        // Set loop detection thresholds: hash hard limit=1 for immediate trigger
                        .loopDetection(100, 1, 100, 200, Duration.ofMinutes(10))
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
                null,
                null,
                null,
                NoopCostTracker.INSTANCE);
    }

    private static List<Content.ToolUseContent> identicalToolCalls() {
        return List.of(new Content.ToolUseContent("id-1", "read_file", Map.of("path", "test.txt")));
    }

    private ReActLoop buildReActLoopWithImmediateHardStop() {
        ReActLoopContext ctx = buildContext();
        return new ReActLoop(
                ctx,
                interrupted,
                currentIteration,
                new java.util.concurrent.atomic.AtomicLong(0),
                () -> null);
    }

    @Test
    void firstLoopDetection_injectsRescuePromptAndContinues() {
        ReActLoop loop = buildReActLoopWithImmediateHardStop();
        List<Msg> history = loop.getHistory();
        int sizeBefore = history.size();

        List<Content.ToolUseContent> calls = identicalToolCalls();
        IterationSignal result = loop.evaluateLoopDetection(calls);

        // Should be a Skip signal (loop rescue)
        assertNotNull(result);
        assertTrue(result instanceof IterationSignal.Skip);
        IterationSignal.Skip skip = (IterationSignal.Skip) result;
        assertTrue(skip.reason().contains("loop rescue"));
        // Rescue prompt should have been injected
        List<Msg> updatedHistory = loop.getHistory();
        assertEquals(sizeBefore + 1, updatedHistory.size());
        Msg rescueMsg = updatedHistory.get(sizeBefore);
        assertEquals(MsgRole.USER, rescueMsg.role());
        Content firstContent = rescueMsg.contents().get(0);
        assertTrue(firstContent.toString().contains("Loop Rescue"));
    }

    @Test
    void secondLoopDetection_returnsLoopDetectedSignal() {
        ReActLoop loop = buildReActLoopWithImmediateHardStop();

        List<Content.ToolUseContent> calls = identicalToolCalls();

        // First detection: injects rescue, returns Skip signal
        IterationSignal firstSignal = loop.evaluateLoopDetection(calls);
        assertNotNull(firstSignal);
        assertTrue(firstSignal instanceof IterationSignal.Skip);

        // Second detection: should return LoopDetected signal
        IterationSignal secondSignal = loop.evaluateLoopDetection(calls);
        assertNotNull(secondSignal);
        assertTrue(secondSignal instanceof IterationSignal.LoopDetected);
    }

    @Test
    void rescuePrompt_hasExpectedContent() {
        ReActLoop loop = buildReActLoopWithImmediateHardStop();

        List<Content.ToolUseContent> calls = identicalToolCalls();
        loop.evaluateLoopDetection(calls);

        List<Msg> history = loop.getHistory();
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
