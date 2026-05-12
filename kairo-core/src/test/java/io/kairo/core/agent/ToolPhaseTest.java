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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.hook.HookChain;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolPhaseTest {

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
                null,
                null,
                null);
    }

    @Test
    void construction_doesNotThrow() {
        ReActLoopContext ctx = buildContext();
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);
        HookDecisionApplier hookDecisions = new HookDecisionApplier(ctx);
        LoopDetector loopDetector = new LoopDetector(100, 200, 5, 10, Duration.ofMinutes(10), 1000);

        assertThatNoException()
                .isThrownBy(
                        () ->
                                new ToolPhase(
                                        ctx,
                                        guards,
                                        hookDecisions,
                                        new ArrayList<>(),
                                        loopDetector,
                                        currentIteration));
    }

    @Test
    void setCompactionTrigger_null_doesNotThrow() {
        ReActLoopContext ctx = buildContext();
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);
        HookDecisionApplier hookDecisions = new HookDecisionApplier(ctx);
        LoopDetector loopDetector = new LoopDetector(100, 200, 5, 10, Duration.ofMinutes(10), 1000);
        ToolPhase phase =
                new ToolPhase(
                        ctx,
                        guards,
                        hookDecisions,
                        new ArrayList<>(),
                        loopDetector,
                        currentIteration);

        assertThatNoException().isThrownBy(() -> phase.setCompactionTrigger(null));
    }
}
