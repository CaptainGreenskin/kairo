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
package io.kairo.core.agent.continuation;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for {@link CompositeContinuationStrategy}. */
class CompositeContinuationStrategyTest {

    private ContinuationContext defaultContext(
            boolean isPlanMode, int iteration, int maxIterations) {
        return new ContinuationContext(
                "test-agent",
                iteration,
                maxIterations,
                List.of(),
                Msg.of(MsgRole.ASSISTANT, "some text"),
                ModelResponse.StopReason.END_TURN,
                0.3f,
                0,
                isPlanMode,
                2,
                Map.of("pendingTodoCount", 3));
    }

    /** Strategy that always returns Nudge. */
    private static AgentContinuationStrategy nudgeStrategy(String name) {
        return new AgentContinuationStrategy() {
            @Override
            public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
                Msg synthetic = Msg.nudge("nudge from " + name, name);
                return Mono.just(new ContinuationDecision.Nudge(synthetic, name + "_nudge"));
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    /** Strategy that always returns Pass. */
    private static AgentContinuationStrategy passStrategy(String name) {
        return new AgentContinuationStrategy() {
            @Override
            public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
                return Mono.just(ContinuationDecision.Pass.INSTANCE);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    @Test
    void decide_firstStrategyReturnsNudge_shortCircuits() {
        CompositeContinuationStrategy composite =
                new CompositeContinuationStrategy(
                        List.of(nudgeStrategy("first"), nudgeStrategy("second")));

        ContinuationContext ctx = defaultContext(false, 5, 50);

        StepVerifier.create(composite.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            assertEquals(
                                    "first_nudge",
                                    ((ContinuationDecision.Nudge) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_firstPassSecondNudge_secondWins() {
        CompositeContinuationStrategy composite =
                new CompositeContinuationStrategy(
                        List.of(passStrategy("first"), nudgeStrategy("second")));

        ContinuationContext ctx = defaultContext(false, 5, 50);

        StepVerifier.create(composite.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            assertEquals(
                                    "second_nudge",
                                    ((ContinuationDecision.Nudge) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_allStrategiesPass_returnsTerminate() {
        CompositeContinuationStrategy composite =
                new CompositeContinuationStrategy(
                        List.of(passStrategy("a"), passStrategy("b"), passStrategy("c")));

        ContinuationContext ctx = defaultContext(false, 5, 50);

        StepVerifier.create(composite.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Terminate.class, decision);
                            assertEquals(
                                    "all_strategies_passed",
                                    ((ContinuationDecision.Terminate) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_planMode_returnsTerminate() {
        CompositeContinuationStrategy composite =
                new CompositeContinuationStrategy(List.of(nudgeStrategy("would_nudge")));

        // isPlanMode=true → forced termination regardless of strategies
        ContinuationContext ctx = defaultContext(true, 5, 50);

        StepVerifier.create(composite.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Terminate.class, decision);
                            assertEquals(
                                    "plan_mode",
                                    ((ContinuationDecision.Terminate) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_noIterationBudget_returnsTerminate() {
        CompositeContinuationStrategy composite =
                new CompositeContinuationStrategy(List.of(nudgeStrategy("would_nudge")));

        // iteration=49, maxIterations=50 → hasIterationBudget() returns false
        ContinuationContext ctx = defaultContext(false, 49, 50);

        StepVerifier.create(composite.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Terminate.class, decision);
                            assertEquals(
                                    "iteration_budget_exhausted",
                                    ((ContinuationDecision.Terminate) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void withDefaults_createsWorkingComposite() {
        CompositeContinuationStrategy composite = CompositeContinuationStrategy.withDefaults();

        assertNotNull(composite);
        assertTrue(composite.name().startsWith("Composite["));
        assertTrue(composite.name().contains("FinishReasonRecovery"));
        assertTrue(composite.name().contains("PendingTodoNudge"));
        assertTrue(composite.name().contains("RecentToolActivity"));
    }

    @Test
    void name_returnsCompositeFormat() {
        CompositeContinuationStrategy composite =
                new CompositeContinuationStrategy(
                        List.of(passStrategy("Alpha"), passStrategy("Beta")));

        assertEquals("Composite[Alpha,Beta]", composite.name());
    }

    @Test
    void decide_emptyStrategiesList_returnsTerminate() {
        CompositeContinuationStrategy composite = new CompositeContinuationStrategy(List.of());

        ContinuationContext ctx = defaultContext(false, 5, 50);

        StepVerifier.create(composite.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Terminate.class, decision);
                            assertEquals(
                                    "all_strategies_passed",
                                    ((ContinuationDecision.Terminate) decision).reason());
                        })
                .verifyComplete();
    }
}
