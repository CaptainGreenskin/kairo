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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HeuristicTokenEstimatorTest {

    private final HeuristicTokenEstimator estimator = new HeuristicTokenEstimator();

    private Msg msg(String text) {
        return Msg.builder().role(MsgRole.USER).addContent(new Content.TextContent(text)).build();
    }

    @Test
    @DisplayName("Empty message list returns 0")
    void emptyList_returnsZero() {
        assertEquals(0, estimator.estimate(List.of()));
    }

    @Test
    @DisplayName("Single message: estimate = chars * 4 / 3")
    void singleMessage_appliesFormula() {
        // 12 chars → 12 * 4 / 3 = 16
        Msg m = msg("Hello World!");
        assertEquals(16, estimator.estimate(List.of(m)));
    }

    @Test
    @DisplayName("Multiple messages: chars are summed before formula")
    void multipleMessages_sumsChars() {
        // "Hello" = 5 chars, "World" = 5 chars → total 10 chars → 10 * 4 / 3 = 13
        assertEquals(13, estimator.estimate(List.of(msg("Hello"), msg("World"))));
    }

    @Test
    @DisplayName("Empty text message contributes 0 chars")
    void emptyTextMessage_contributesZero() {
        Msg empty = msg("");
        Msg nonempty = msg("abc"); // 3 chars → 3 * 4 / 3 = 4
        assertEquals(4, estimator.estimate(List.of(empty, nonempty)));
    }

    @Test
    @DisplayName("Estimate is consistently repeatable")
    void estimate_isRepeatable() {
        List<Msg> msgs = List.of(msg("test message content"));
        int first = estimator.estimate(msgs);
        int second = estimator.estimate(msgs);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("Message with tokenCount set: estimate still uses text length (heuristic)")
    void messageWithTokenCount_usesTextNotStoredCount() {
        // HeuristicTokenEstimator uses msg.text().length(), not msg.tokenCount()
        Msg m =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("Hi")) // 2 chars → 2
                        .tokenCount(9999)
                        .build();
        // 2 * 4 / 3 = 2
        assertEquals(2, estimator.estimate(List.of(m)));
    }

    @Test
    @DisplayName("Long text yields proportionally larger estimate")
    void longText_largerEstimate() {
        String shortText = "a".repeat(100);
        String longText = "a".repeat(300);
        int shortEst = estimator.estimate(List.of(msg(shortText)));
        int longEst = estimator.estimate(List.of(msg(longText)));
        assertTrue(longEst > shortEst);
    }
}
