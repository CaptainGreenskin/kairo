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
import reactor.test.StepVerifier;

/** Unit tests for {@link PendingTodoNudgeStrategy}. */
class PendingTodoNudgeStrategyTest {

    private ContinuationContext baseContext(
            int pendingTodoCount, int nudgesApplied, int iteration, int maxIterations) {
        return new ContinuationContext(
                "test-agent",
                iteration,
                maxIterations,
                List.of(),
                Msg.of(MsgRole.ASSISTANT, "progress update"),
                ModelResponse.StopReason.END_TURN,
                0.3f,
                nudgesApplied,
                false,
                0,
                Map.of("pendingTodoCount", pendingTodoCount));
    }

    @Test
    void decide_pendingTodosPresent_returnsNudge() {
        PendingTodoNudgeStrategy strategy = new PendingTodoNudgeStrategy();

        ContinuationContext ctx = baseContext(3, 0, 5, 50);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            ContinuationDecision.Nudge nudge =
                                    (ContinuationDecision.Nudge) decision;
                            assertTrue(
                                    nudge.syntheticUserMessage()
                                            .text()
                                            .contains("3 unfinished TODO"));
                            assertEquals("pending_todos=3", nudge.reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_pendingTodosZero_returnsPass() {
        PendingTodoNudgeStrategy strategy = new PendingTodoNudgeStrategy();

        ContinuationContext ctx = baseContext(0, 0, 5, 50);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(decision -> assertInstanceOf(ContinuationDecision.Pass.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_noPendingTodoCountKey_returnsPass() {
        PendingTodoNudgeStrategy strategy = new PendingTodoNudgeStrategy();

        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        30,
                        50,
                        List.of(),
                        Msg.of(MsgRole.ASSISTANT, "done"),
                        ModelResponse.StopReason.END_TURN,
                        0.3f,
                        0,
                        false,
                        0,
                        Map.of());

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(decision -> assertInstanceOf(ContinuationDecision.Pass.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_hasIncompletePlan_earlyIteration_returnsNudge() {
        PendingTodoNudgeStrategy strategy = new PendingTodoNudgeStrategy();

        // iteration=5, maxIterations=50 → ratio < 50%, budget available
        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        5,
                        50,
                        List.of(),
                        Msg.of(MsgRole.ASSISTANT, "thinking"),
                        ModelResponse.StopReason.END_TURN,
                        0.3f,
                        0,
                        false,
                        0,
                        Map.of("hasIncompletePlan", true));

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            ContinuationDecision.Nudge nudge =
                                    (ContinuationDecision.Nudge) decision;
                            assertEquals("incomplete_plan_detected", nudge.reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_hasIncompletePlan_lateIteration_returnsPass() {
        PendingTodoNudgeStrategy strategy = new PendingTodoNudgeStrategy();

        // iteration=30, maxIterations=50 → ratio >= 50%, so incomplete_plan check skipped
        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        30,
                        50,
                        List.of(),
                        Msg.of(MsgRole.ASSISTANT, "late stage"),
                        ModelResponse.StopReason.END_TURN,
                        0.3f,
                        0,
                        false,
                        0,
                        Map.of("hasIncompletePlan", true));

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(decision -> assertInstanceOf(ContinuationDecision.Pass.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_priorNudgesApplied_stillNudgesIfTodosRemain() {
        // Signal-based redesign: prior nudge count does not exhaust the strategy.
        // LoopDetector + iteration budget + token budget are the guards instead.
        PendingTodoNudgeStrategy strategy = new PendingTodoNudgeStrategy();

        ContinuationContext ctx = baseContext(5, 20, 5, 50);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> assertInstanceOf(ContinuationDecision.Nudge.class, decision))
                .verifyComplete();
    }

    @Test
    void name_returnsPendingTodoNudge() {
        assertEquals("PendingTodoNudge", new PendingTodoNudgeStrategy().name());
    }
}
