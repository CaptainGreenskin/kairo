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

/** Unit tests for {@link RecentToolActivityStrategy}. */
class RecentToolActivityStrategyTest {

    private ContinuationContext contextWithToolActivity(int toolCallsInLastK, int nudgesApplied) {
        return new ContinuationContext(
                "test-agent",
                5,
                50,
                List.of(),
                Msg.of(MsgRole.ASSISTANT, "narrating progress"),
                ModelResponse.StopReason.END_TURN,
                0.3f,
                nudgesApplied,
                false,
                toolCallsInLastK,
                Map.of());
    }

    @Test
    void decide_recentToolActivity_returnsNudge() {
        RecentToolActivityStrategy strategy = new RecentToolActivityStrategy(3);

        ContinuationContext ctx = contextWithToolActivity(5, 0);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            ContinuationDecision.Nudge nudge =
                                    (ContinuationDecision.Nudge) decision;
                            assertTrue(
                                    nudge.syntheticUserMessage()
                                            .text()
                                            .contains("Continue executing"));
                            assertTrue(nudge.reason().contains("recent_tools=5"));
                        })
                .verifyComplete();
    }

    @Test
    void decide_noRecentToolActivity_returnsPass() {
        RecentToolActivityStrategy strategy = new RecentToolActivityStrategy(3);

        ContinuationContext ctx = contextWithToolActivity(0, 0);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(decision -> assertInstanceOf(ContinuationDecision.Pass.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_nudgesAlreadyApplied_stillNudges() {
        // Signal-based redesign: prior nudges do not exhaust the strategy.
        // LoopDetector + iteration budget + token budget are the guards instead.
        RecentToolActivityStrategy strategy = new RecentToolActivityStrategy(3);

        ContinuationContext ctx = contextWithToolActivity(5, 20);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> assertInstanceOf(ContinuationDecision.Nudge.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_singleToolCall_returnsNudge() {
        RecentToolActivityStrategy strategy = new RecentToolActivityStrategy(3);

        // Even 1 tool call in the lookback window should trigger nudge
        ContinuationContext ctx = contextWithToolActivity(1, 0);

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Nudge.class, decision);
                            assertTrue(
                                    ((ContinuationDecision.Nudge) decision)
                                            .reason()
                                            .contains("recent_tools=1_in_last_3"));
                        })
                .verifyComplete();
    }

    @Test
    void name_returnsRecentToolActivity() {
        assertEquals("RecentToolActivity", new RecentToolActivityStrategy().name());
    }

    @Test
    void decide_longAssistantText_returnsPass() {
        // A long structured answer is a final answer to the user, not mid-task narration.
        // Even with recent tool activity, the strategy must NOT nudge — otherwise the agent
        // re-prompts itself after delivering the result, burning one extra iteration.
        RecentToolActivityStrategy strategy = new RecentToolActivityStrategy(3);

        String longAnswer = "The file contains a Python script that ".repeat(20);
        assertTrue(longAnswer.length() >= 200);

        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        5,
                        50,
                        List.of(),
                        Msg.of(MsgRole.ASSISTANT, longAnswer),
                        ModelResponse.StopReason.END_TURN,
                        0.3f,
                        0,
                        false,
                        5,
                        Map.of());

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(decision -> assertInstanceOf(ContinuationDecision.Pass.class, decision))
                .verifyComplete();
    }

    @Test
    void decide_nullLastAssistantMsg_stillNudges() {
        // Defensive: if the message is null, fall back to existing tool-activity signal so we
        // don't silently lose narration detection.
        RecentToolActivityStrategy strategy = new RecentToolActivityStrategy(3);

        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        5,
                        50,
                        List.of(),
                        null,
                        ModelResponse.StopReason.END_TURN,
                        0.3f,
                        0,
                        false,
                        5,
                        Map.of());

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> assertInstanceOf(ContinuationDecision.Nudge.class, decision))
                .verifyComplete();
    }
}
