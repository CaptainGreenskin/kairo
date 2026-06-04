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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HeuristicTokenEstimatorTest {

    private final HeuristicTokenEstimator estimator = new HeuristicTokenEstimator();

    private Msg textMsg(String text) {
        return Msg.builder().role(MsgRole.USER).addContent(new Content.TextContent(text)).build();
    }

    @Test
    @DisplayName("Empty message list returns 0")
    void emptyList_returnsZero() {
        assertEquals(0, estimator.estimate(List.of()));
    }

    @Test
    @DisplayName("TextContent: chars * 2 / 7 ≈ chars/3.5")
    void textContent_appliesCoefficient() {
        // 70 chars → 70 * 2 / 7 = 20
        Msg m = textMsg("a".repeat(70));
        assertEquals(20, estimator.estimate(List.of(m)));
    }

    @Test
    @DisplayName("ThinkingContent: same coefficient as text")
    void thinkingContent_appliesCoefficient() {
        // 35 chars → 35 * 2 / 7 = 10
        Msg m =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.ThinkingContent("x".repeat(35), 0, "sig"))
                        .build();
        assertEquals(10, estimator.estimate(List.of(m)));
    }

    @Test
    @DisplayName("ToolUseContent: chars / 5 (sparse JSON)")
    void toolUseContent_appliesJsonCoefficient() {
        // input map toString ≈ "{path=test.java}" = 16 chars → 16 / 5 = 3
        Msg m =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(
                                new Content.ToolUseContent(
                                        "id-1", "read_file", Map.of("path", "test.java")))
                        .build();
        int result = estimator.estimate(List.of(m));
        assertTrue(result >= 1, "ToolUseContent should produce at least 1 token");
    }

    @Test
    @DisplayName("ToolResultContent: chars * 2 / 9 ≈ chars/4.5")
    void toolResultContent_appliesCoefficient() {
        // 90 chars → 90 * 2 / 9 = 20
        Msg m =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.ToolResultContent("id-1", "r".repeat(90), false))
                        .build();
        assertEquals(20, estimator.estimate(List.of(m)));
    }

    @Test
    @DisplayName("Mixed content types sum individually")
    void mixedContent_sumsPerBlock() {
        // text: 70 chars → 20, tool result: 90 chars → 20 → total 40
        Msg m =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("a".repeat(70)))
                        .addContent(new Content.ToolResultContent("id-1", "r".repeat(90), false))
                        .build();
        assertEquals(40, estimator.estimate(List.of(m)));
    }

    @Test
    @DisplayName("Each content block produces at least 1 token")
    void singleChar_producesAtLeastOne() {
        Msg m = textMsg("x");
        assertEquals(1, estimator.estimate(List.of(m)));
    }

    @Test
    @DisplayName("Multiple messages are summed")
    void multipleMessages_sumsAll() {
        // 70 chars each → 20 + 20 = 40
        assertEquals(
                40, estimator.estimate(List.of(textMsg("a".repeat(70)), textMsg("b".repeat(70)))));
    }

    @Test
    @DisplayName("Estimate is consistently repeatable")
    void estimate_isRepeatable() {
        List<Msg> msgs = List.of(textMsg("test message content"));
        int first = estimator.estimate(msgs);
        int second = estimator.estimate(msgs);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("Long text yields proportionally larger estimate")
    void longText_largerEstimate() {
        int shortEst = estimator.estimate(List.of(textMsg("a".repeat(100))));
        int longEst = estimator.estimate(List.of(textMsg("a".repeat(300))));
        assertTrue(longEst > shortEst);
    }

    @Test
    @DisplayName("ToolUseContent with null input produces 1 token")
    void toolUseContent_nullInput_producesOne() {
        Msg m =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.ToolUseContent("id-1", "noop", null))
                        .build();
        assertEquals(1, estimator.estimate(List.of(m)));
    }
}
