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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeuristicTokenEstimatorTest {

    private final HeuristicTokenEstimator estimator = new HeuristicTokenEstimator();

    @Test
    void emptyListReturnsZero() {
        assertThat(estimator.estimate(List.of())).isZero();
    }

    @Test
    void singleMessageEstimatesCharsTimes4Over3() {
        // "hello" = 5 chars; 5 * 4 / 3 = 6 (integer division)
        Msg msg = Msg.of(MsgRole.USER, "hello");
        assertThat(estimator.estimate(List.of(msg))).isEqualTo(5 * 4 / 3);
    }

    @Test
    void estimateIsPositiveForNonEmptyMessage() {
        Msg msg = Msg.of(MsgRole.USER, "hello world");
        assertThat(estimator.estimate(List.of(msg))).isGreaterThan(0);
    }

    @Test
    void multipleMessagesAreSummed() {
        Msg a = Msg.of(MsgRole.USER, "hello");
        Msg b = Msg.of(MsgRole.ASSISTANT, "world!");
        int expected = ("hello".length() + "world!".length()) * 4 / 3;
        assertThat(estimator.estimate(List.of(a, b))).isEqualTo(expected);
    }

    @Test
    void longerMessageGivesHigherEstimate() {
        Msg short1 = Msg.of(MsgRole.USER, "hi");
        Msg long1 = Msg.of(MsgRole.USER, "hello world, this is a longer message");
        assertThat(estimator.estimate(List.of(long1)))
                .isGreaterThan(estimator.estimate(List.of(short1)));
    }

    @Test
    void implementsTokenEstimatorInterface() {
        assertThat(estimator).isInstanceOf(TokenEstimator.class);
    }
}
