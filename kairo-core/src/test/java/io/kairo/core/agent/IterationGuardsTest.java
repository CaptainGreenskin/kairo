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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.hook.HookChain;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IterationGuardsTest {

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

    private ReActLoopContext buildContext(int maxIterations, int tokenBudget) {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(maxIterations)
                        .tokenBudget(tokenBudget)
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
    void evaluate_belowMaxIterations_returnsEmpty() {
        ReActLoopContext ctx = buildContext(10, 200_000);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);
        currentIteration.set(3);

        Msg result = guards.evaluate().block();

        assertThat(result).isNull();
    }

    @Test
    void evaluate_atMaxIterations_returnsFinalMessage() {
        ReActLoopContext ctx = buildContext(5, 200_000);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);
        currentIteration.set(5);

        Msg result = guards.evaluate().block();

        assertThat(result).isNotNull();
        assertThat(result.text()).contains("maximum iteration limit");
    }

    @Test
    void evaluate_customMaxIterations_triggersAtCustomLimit() {
        ReActLoopContext ctx = buildContext(3, 200_000);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);
        currentIteration.set(3);

        Msg result = guards.evaluate().block();

        assertThat(result).isNotNull();
        assertThat(result.text()).contains("maximum iteration limit");
    }

    @Test
    void evaluate_interrupted_throwsAgentInterruptedException() {
        ReActLoopContext ctx = buildContext(10, 200_000);
        interrupted.set(true);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);

        assertThatThrownBy(() -> guards.evaluate().block())
                .isInstanceOf(AgentInterruptedException.class);
    }

    @Test
    void evaluate_tokenBudgetExceeded_returnsFinalMessage() {
        // tokenBudget of 0 means any usage triggers the budget check
        ReActLoopContext ctx = buildContext(10, 0);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);

        Msg result = guards.evaluate().block();

        assertThat(result).isNotNull();
        assertThat(result.text()).contains("token budget");
    }

    @Test
    void evaluate_shutdown_returnsFinalMessage() {
        shutdownManager.performShutdown();
        ReActLoopContext ctx = buildContext(10, 200_000);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);

        Msg result = guards.evaluate().block();

        assertThat(result).isNotNull();
        assertThat(result.text()).contains("shutdown");
    }

    @Test
    void checkCancelled_notInterrupted_returnsEmpty() {
        ReActLoopContext ctx = buildContext(10, 200_000);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);

        Void result = guards.checkCancelled().block();

        assertThat(result).isNull();
    }

    @Test
    void checkCancelled_interrupted_throwsAgentInterruptedException() {
        ReActLoopContext ctx = buildContext(10, 200_000);
        interrupted.set(true);
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);

        assertThatThrownBy(() -> guards.checkCancelled().block())
                .isInstanceOf(AgentInterruptedException.class);
    }
}
