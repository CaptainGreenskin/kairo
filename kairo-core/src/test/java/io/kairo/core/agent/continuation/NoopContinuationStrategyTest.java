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

/** Unit tests for {@link NoopContinuationStrategy}. */
class NoopContinuationStrategyTest {

    @Test
    void decide_alwaysReturnsTerminateWithNoopReason() {
        NoopContinuationStrategy strategy = NoopContinuationStrategy.INSTANCE;

        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        5,
                        50,
                        List.of(),
                        Msg.of(MsgRole.ASSISTANT, "some text"),
                        ModelResponse.StopReason.END_TURN,
                        0.3f,
                        0,
                        false,
                        2,
                        Map.of("pendingTodoCount", 3));

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Terminate.class, decision);
                            assertEquals(
                                    "noop", ((ContinuationDecision.Terminate) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void decide_returnsTerminateRegardlessOfStopReason() {
        NoopContinuationStrategy strategy = NoopContinuationStrategy.INSTANCE;

        ContinuationContext ctx =
                new ContinuationContext(
                        "test-agent",
                        0,
                        100,
                        List.of(),
                        Msg.of(MsgRole.ASSISTANT, "cut off"),
                        ModelResponse.StopReason.MAX_TOKENS,
                        0.9f,
                        5,
                        true,
                        10,
                        Map.of());

        StepVerifier.create(strategy.decide(ctx))
                .assertNext(
                        decision -> {
                            assertInstanceOf(ContinuationDecision.Terminate.class, decision);
                            assertEquals(
                                    "noop", ((ContinuationDecision.Terminate) decision).reason());
                        })
                .verifyComplete();
    }

    @Test
    void name_returnsNoop() {
        assertEquals("Noop", NoopContinuationStrategy.INSTANCE.name());
    }
}
