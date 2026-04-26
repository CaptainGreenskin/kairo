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
package io.kairo.core.context;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeuristicTokenEstimatorTest {

    private HeuristicTokenEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new HeuristicTokenEstimator();
    }

    @Test
    void implementsTokenEstimator() {
        assertInstanceOf(TokenEstimator.class, estimator);
    }

    @Test
    void emptyListReturnsZero() {
        assertEquals(0, estimator.estimate(List.of()));
    }

    @Test
    void singleMessageUsesCharTimesRatio() {
        // "hello" = 5 chars → 5 * 4 / 3 = 6 (integer division)
        Msg msg = Msg.of(MsgRole.USER, "hello");
        int result = estimator.estimate(List.of(msg));
        assertEquals(5 * 4 / 3, result);
    }

    @Test
    void twelveCharMessageGivesSixteenTokens() {
        // "123456789012" = 12 chars → 12 * 4 / 3 = 16
        Msg msg = Msg.of(MsgRole.USER, "123456789012");
        assertEquals(16, estimator.estimate(List.of(msg)));
    }

    @Test
    void multipleMessagesAreSummed() {
        // "aaa" = 3 chars, "bbb" = 3 chars → total 6 chars → 6 * 4 / 3 = 8
        Msg m1 = Msg.of(MsgRole.USER, "aaa");
        Msg m2 = Msg.of(MsgRole.ASSISTANT, "bbb");
        assertEquals(8, estimator.estimate(List.of(m1, m2)));
    }

    @Test
    void resultIsAlwaysNonNegative() {
        Msg msg = Msg.of(MsgRole.USER, "x");
        assertTrue(estimator.estimate(List.of(msg)) >= 0);
    }

    @Test
    void longerTextGivesHigherEstimate() {
        Msg short_ = Msg.of(MsgRole.USER, "hi");
        Msg long_ = Msg.of(MsgRole.USER, "a".repeat(100));
        assertTrue(estimator.estimate(List.of(long_)) > estimator.estimate(List.of(short_)));
    }
}
