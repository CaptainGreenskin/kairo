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
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.execution.*;
import io.kairo.api.hook.HookChain;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.execution.DefaultResourceConstraint;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Tests for {@link IterationGuards} with {@link ResourceConstraint} integration.
 *
 * <p>Validates both the new constraint delegation path and backward-compatible fallback.
 */
@DisplayName("IterationGuards — ResourceConstraint integration")
class IterationGuardsConstraintTest {

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
                null);
    }

    @Test
    @DisplayName("with ResourceConstraint — iteration limit triggers graceful exit message")
    void constraintIterationLimit_triggersGracefulExit() {
        ReActLoopContext ctx = buildContext(10, 200_000);
        ResourceConstraint constraint =
                new DefaultResourceConstraint(5, Long.MAX_VALUE, Duration.ofHours(1));

        IterationGuards guards =
                new IterationGuards(ctx, interrupted, currentIteration, List.of(constraint));

        currentIteration.set(5);
        Msg result = guards.evaluate().block();
        assertNotNull(result, "Should return a final message when iteration limit is reached");
        assertTrue(result.text().contains("max iterations"));
        assertTrue(result.text().contains("Here is what I have so far"));
    }

    @Test
    @DisplayName("with ResourceConstraint — token budget triggers graceful exit message")
    void constraintTokenBudget_triggersGracefulExit() {
        // Set up token budget manager to report high usage
        tokenBudgetManager = new TokenBudgetManager(200_000, 8_096);
        // TokenBudgetManager tracks tokens internally via totalAccountedTokens()
        // The DefaultResourceConstraint uses context.tokensUsed() which comes from
        // tokenBudgetManager.totalAccountedTokens() — set a low budget so 0 tokens exceeds it

        ReActLoopContext ctx = buildContext(10, 200_000);
        // Set tokenBudget to 0 so any usage (even 0) triggers violation via >=
        ResourceConstraint constraint = new DefaultResourceConstraint(100, 0L, Duration.ofHours(1));

        IterationGuards guards =
                new IterationGuards(ctx, interrupted, currentIteration, List.of(constraint));

        currentIteration.set(0);
        Msg result = guards.evaluate().block();
        assertNotNull(result, "Should return a final message when token budget is exceeded");
        assertTrue(result.text().contains("token budget"));
    }

    @Test
    @DisplayName("EMERGENCY_STOP wins over GRACEFUL_EXIT")
    void emergencyStop_winsOverGracefulExit() {
        ReActLoopContext ctx = buildContext(10, 200_000);

        // A constraint that returns GRACEFUL_EXIT
        ResourceConstraint gracefulConstraint =
                new ResourceConstraint() {
                    @Override
                    public Mono<ResourceValidation> validate(ResourceContext context) {
                        return Mono.just(
                                ResourceValidation.violated(
                                        "graceful reason", Map.of("type", "graceful")));
                    }

                    @Override
                    public ResourceAction onViolation(ResourceValidation validation) {
                        return ResourceAction.GRACEFUL_EXIT;
                    }
                };

        // A constraint that returns EMERGENCY_STOP
        ResourceConstraint emergencyConstraint =
                new ResourceConstraint() {
                    @Override
                    public Mono<ResourceValidation> validate(ResourceContext context) {
                        return Mono.just(
                                ResourceValidation.violated(
                                        "emergency reason", Map.of("type", "emergency")));
                    }

                    @Override
                    public ResourceAction onViolation(ResourceValidation validation) {
                        return ResourceAction.EMERGENCY_STOP;
                    }
                };

        IterationGuards guards =
                new IterationGuards(
                        ctx,
                        interrupted,
                        currentIteration,
                        List.of(gracefulConstraint, emergencyConstraint));

        Msg result = guards.evaluate().block();
        assertNotNull(result);
        assertTrue(
                result.text().contains("Stopping immediately"),
                "EMERGENCY_STOP should win, got: " + result.text());
    }

    @Test
    @DisplayName("without ResourceConstraint — fallback to existing behavior (backward compat)")
    void noConstraint_fallbackToExistingBehavior() {
        ReActLoopContext ctx = buildContext(5, 200_000);

        // No constraints — use original constructor
        IterationGuards guards = new IterationGuards(ctx, interrupted, currentIteration);

        currentIteration.set(5);
        Msg result = guards.evaluate().block();
        assertNotNull(result, "Should return final message via inline check");
        assertTrue(
                result.text().contains("maximum iteration limit"),
                "Should use original message, got: " + result.text());
    }

    @Test
    @DisplayName("interrupt check still works with ResourceConstraint present")
    void interruptCheck_worksWithConstraints() {
        ReActLoopContext ctx = buildContext(10, 200_000);
        ResourceConstraint constraint =
                new DefaultResourceConstraint(100, Long.MAX_VALUE, Duration.ofHours(1));

        IterationGuards guards =
                new IterationGuards(ctx, interrupted, currentIteration, List.of(constraint));

        interrupted.set(true);
        assertThrows(AgentInterruptedException.class, () -> guards.evaluate().block());
    }

    @Test
    @DisplayName("shutdown check still works with ResourceConstraint present")
    void shutdownCheck_worksWithConstraints() {
        shutdownManager.performShutdown();
        ReActLoopContext ctx = buildContext(10, 200_000);
        ResourceConstraint constraint =
                new DefaultResourceConstraint(100, Long.MAX_VALUE, Duration.ofHours(1));

        IterationGuards guards =
                new IterationGuards(ctx, interrupted, currentIteration, List.of(constraint));

        Msg result = guards.evaluate().block();
        assertNotNull(result);
        assertTrue(
                result.text().contains("shutdown"),
                "Should return shutdown message, got: " + result.text());
    }
}
