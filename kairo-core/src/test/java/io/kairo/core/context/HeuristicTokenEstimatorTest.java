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

/** Unit tests for {@link HeuristicTokenEstimator}. */
class HeuristicTokenEstimatorTest {

    private final HeuristicTokenEstimator estimator = new HeuristicTokenEstimator();

    @Test
    void emptyList_returnsZero() {
        assertThat(estimator.estimate(List.of())).isEqualTo(0);
    }

    @Test
    void singleMessage_returnsCharsTimesFourThirds() {
        // "hello" = 5 chars; 5 * 4 / 3 = 6 (integer division)
        Msg msg = Msg.of(MsgRole.USER, "hello");
        assertThat(estimator.estimate(List.of(msg))).isEqualTo(5 * 4 / 3);
    }

    @Test
    void multipleMessages_sumsThenScales() {
        // "abc" = 3 chars, "de" = 2 chars → total 5 * 4 / 3 = 6
        Msg m1 = Msg.of(MsgRole.USER, "abc");
        Msg m2 = Msg.of(MsgRole.ASSISTANT, "de");
        assertThat(estimator.estimate(List.of(m1, m2))).isEqualTo(5 * 4 / 3);
    }

    @Test
    void emptyTextMessage_doesNotCrash() {
        Msg msg = Msg.of(MsgRole.USER, "");
        assertThat(estimator.estimate(List.of(msg))).isEqualTo(0);
    }

    @Test
    void result_isAlwaysNonNegative() {
        Msg msg = Msg.of(MsgRole.USER, "x");
        assertThat(estimator.estimate(List.of(msg))).isGreaterThanOrEqualTo(0);
    }

    @Test
    void longerText_producesLargerEstimate() {
        Msg short1 = Msg.of(MsgRole.USER, "hi");
        Msg long1 = Msg.of(MsgRole.USER, "hello world, this is a longer message");
        assertThat(estimator.estimate(List.of(long1)))
                .isGreaterThan(estimator.estimate(List.of(short1)));
    }

    @Test
    void knownExactValue_120chars() {
        // 120 chars * 4 / 3 = 160
        String text = "a".repeat(120);
        Msg msg = Msg.of(MsgRole.USER, text);
        assertThat(estimator.estimate(List.of(msg))).isEqualTo(160);
    }
}
