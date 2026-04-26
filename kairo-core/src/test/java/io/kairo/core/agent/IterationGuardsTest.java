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
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.message.Msg;
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
import reactor.test.StepVerifier;

/** Unit tests for {@link IterationGuards}. */
class IterationGuardsTest {

    private static final int MAX_ITERATIONS = 5;
    private static final int TOKEN_BUDGET = 1000;

    private GracefulShutdownManager shutdownManager;
    private TokenBudgetManager tokenBudgetManager;

    @BeforeEach
    void setUp() {
        shutdownManager = new GracefulShutdownManager();
        tokenBudgetManager = new TokenBudgetManager(TOKEN_BUDGET, 100);
    }

    private ReActLoopContext buildCtx(int maxIterations, int tokenBudget) {
        AgentConfig config =
                AgentConfig.builder()
                        .name("guard-agent")
                        .modelProvider(mock(io.kairo.api.model.ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(maxIterations)
                        .tokenBudget(tokenBudget)
                        .build();

        ErrorRecoveryStrategy errorRecovery =
                new ErrorRecoveryStrategy(
                        mock(io.kairo.api.model.ModelProvider.class),
                        null,
                        new ModelFallbackManager(List.of()));

        return new ReActLoopContext(
                "agent-guard-id",
                "guard-agent",
                config,
                new DefaultHookChain(),
                null,
                mock(ToolExecutor.class),
                errorRecovery,
                tokenBudgetManager,
                shutdownManager,
                null,
                null);
    }

    private IterationGuards guards(
            ReActLoopContext ctx, AtomicBoolean interrupted, AtomicInteger iteration) {
        return new IterationGuards(ctx, interrupted, iteration);
    }

    // ===== Guards not triggered =====

    @Test
    void evaluate_noGuardsTriggered_returnsEmpty() {
        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g = guards(ctx, new AtomicBoolean(false), new AtomicInteger(0));

        StepVerifier.create(g.evaluate()).verifyComplete();
    }

    // ===== Max iterations =====

    @Test
    void evaluate_atMaxIterations_returnsFinalMsg() {
        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g =
                guards(ctx, new AtomicBoolean(false), new AtomicInteger(MAX_ITERATIONS));

        StepVerifier.create(g.evaluate())
                .assertNext(
                        msg -> {
                            assertThat(msg).isNotNull();
                            assertThat(msg.text()).containsIgnoringCase("iteration");
                        })
                .verifyComplete();
    }

    @Test
    void evaluate_belowMaxIterations_doesNotTrigger() {
        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g =
                guards(ctx, new AtomicBoolean(false), new AtomicInteger(MAX_ITERATIONS - 1));

        StepVerifier.create(g.evaluate()).verifyComplete();
    }

    // ===== Token budget =====

    @Test
    void evaluate_tokenBudgetExceeded_returnsFinalMsg() {
        // Record usage that exceeds budget
        tokenBudgetManager.recordUsage(TOKEN_BUDGET + 1);

        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g = guards(ctx, new AtomicBoolean(false), new AtomicInteger(0));

        StepVerifier.create(g.evaluate())
                .assertNext(
                        msg -> {
                            assertThat(msg).isNotNull();
                            assertThat(msg.text()).containsIgnoringCase("token");
                        })
                .verifyComplete();
    }

    // ===== Interruption =====

    @Test
    void evaluate_interrupted_returnsError() {
        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g = guards(ctx, new AtomicBoolean(true), new AtomicInteger(0));

        StepVerifier.create(g.evaluate()).expectError(AgentInterruptedException.class).verify();
    }

    // ===== checkCancelled =====

    @Test
    void checkCancelled_notInterrupted_returnsEmpty() {
        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g = guards(ctx, new AtomicBoolean(false), new AtomicInteger(0));

        StepVerifier.create(g.checkCancelled()).verifyComplete();
    }

    @Test
    void checkCancelled_interrupted_returnsError() {
        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g = guards(ctx, new AtomicBoolean(true), new AtomicInteger(2));

        StepVerifier.create(g.checkCancelled())
                .expectError(AgentInterruptedException.class)
                .verify();
    }

    // ===== buildFinalResponse =====

    @Test
    void buildFinalResponse_returnsAssistantMsg() {
        ReActLoopContext ctx = buildCtx(MAX_ITERATIONS, TOKEN_BUDGET);
        IterationGuards g = guards(ctx, new AtomicBoolean(false), new AtomicInteger(0));

        Msg msg = g.buildFinalResponse("Done.");
        assertThat(msg).isNotNull();
        assertThat(msg.text()).isEqualTo("Done.");
    }
}
