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

/** Unit tests for {@link FinishReasonRecoveryStrategy}. */
class FinishReasonRecoveryStrategyTest {

    private ContinuationContext contextWithStopReason(
            ModelResponse.StopReason stopReason, int nudgesApplied) {
        return new ContinuationContext(
                "test-agent",
                5,
                50,
                List.of(),
                Msg.of(MsgRole.ASSISTANT, "partial response"),
                stopReason,
                0.3f,
                nudgesApplied,
                false,
                2,
                Map.of());
    }

    @Test
    void decide_maxTokensWithinBudget_returnsNudge() {
        FinishReasonRecoveryStrategy strategy = new FinishReasonRecoveryStrategy(3);

        ContinuationContext ctx = contextWithStopReason(ModelResponse.StopReason.MAX_TOKENS, 0);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            ContinuationDecision.Nudge nudge =
                                    (ContinuationDecision.Nudge) decision;
                            assertTrue(
                                    nudge.syntheticUserMessage()
                                            .text()
                                            .contains("cut off due to length"));
                            assertEquals("stop_reason=max_tokens", nudge.reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_endTurn_returnsPass() {
        FinishReasonRecoveryStrategy strategy = new FinishReasonRecoveryStrategy(3);

        ContinuationContext ctx = contextWithStopReason(ModelResponse.StopReason.END_TURN, 0);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(decision -> assertInstanceOf(ContinuationDecision.Pass.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_toolUse_returnsPass() {
        FinishReasonRecoveryStrategy strategy = new FinishReasonRecoveryStrategy(3);

        ContinuationContext ctx = contextWithStopReason(ModelResponse.StopReason.TOOL_USE, 0);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(decision -> assertInstanceOf(ContinuationDecision.Pass.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_maxTokensBudgetExhausted_returnsTerminate() {
        FinishReasonRecoveryStrategy strategy = new FinishReasonRecoveryStrategy(3);

        // nudgesApplied=3 >= maxRetries=3
        ContinuationContext ctx = contextWithStopReason(ModelResponse.StopReason.MAX_TOKENS, 3);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Terminate.class, decision);
                            assertEquals(
                                    "length_exhausted",
                                    ((ContinuationDecision.Terminate) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_nullStopReason_handledGracefully() {
        FinishReasonRecoveryStrategy strategy = new FinishReasonRecoveryStrategy(3);

        // null stopReason — should not be MAX_TOKENS, so returns Pass
        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        5,
                        50,
                        List.of(),
                        Msg.of(MsgRole.ASSISTANT, "done"),
                        null,
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
    void decide_maxTokensAtBudgetBoundary_returnsNudge() {
        FinishReasonRecoveryStrategy strategy = new FinishReasonRecoveryStrategy(3);

        // nudgesApplied=2, maxRetries=3 → still within budget
        ContinuationContext ctx = contextWithStopReason(ModelResponse.StopReason.MAX_TOKENS, 2);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            assertEquals(
                                    "stop_reason=max_tokens",
                                    ((ContinuationDecision.Nudge) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void name_returnsFinishReasonRecovery() {
        assertEquals("FinishReasonRecovery", new FinishReasonRecoveryStrategy().name());
    }
}
