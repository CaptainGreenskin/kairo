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
    void implementsTokenEstimator() {
        assertThat(estimator).isInstanceOf(TokenEstimator.class);
    }

    @Test
    void emptyMessageList_returnsZero() {
        assertThat(estimator.estimate(List.of())).isZero();
    }

    @Test
    void singleMessage_returnsNonNegative() {
        Msg msg = Msg.of(MsgRole.USER, "hello");
        assertThat(estimator.estimate(List.of(msg))).isGreaterThanOrEqualTo(0);
    }

    @Test
    void longerMessage_givesHigherEstimate() {
        Msg short1 = Msg.of(MsgRole.USER, "hi");
        Msg long1 = Msg.of(MsgRole.USER, "this is a much longer message with more tokens");
        assertThat(estimator.estimate(List.of(long1)))
                .isGreaterThan(estimator.estimate(List.of(short1)));
    }

    @Test
    void twoMessages_greaterThanOne() {
        Msg msg = Msg.of(MsgRole.USER, "hello world");
        assertThat(estimator.estimate(List.of(msg, msg)))
                .isGreaterThan(estimator.estimate(List.of(msg)));
    }

    @Test
    void knownInput_matchesFormula() {
        // 12 chars * 4 / 3 = 16
        Msg msg = Msg.of(MsgRole.USER, "123456789012");
        assertThat(estimator.estimate(List.of(msg))).isEqualTo(16);
    }

    @Test
    void emptyStringMessage_returnsZero() {
        Msg msg = Msg.of(MsgRole.USER, "");
        assertThat(estimator.estimate(List.of(msg))).isZero();
    }

    @Test
    void multipleMessages_greaterThanEachAlone() {
        Msg a = Msg.of(MsgRole.USER, "first message");
        Msg b = Msg.of(MsgRole.ASSISTANT, "second message reply");
        int two = estimator.estimate(List.of(a, b));
        assertThat(two).isGreaterThan(estimator.estimate(List.of(a)));
        assertThat(two).isGreaterThan(estimator.estimate(List.of(b)));
    }
}
