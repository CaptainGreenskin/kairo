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
package io.kairo.core.model.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.IntRange;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComplexityEstimatorTest {

    private ComplexityEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new ComplexityEstimator();
    }

    @Test
    void nullMessagesReturnsOne() {
        assertEquals(1, estimator.estimateComplexity(null));
    }

    @Test
    void emptyMessagesReturnsOne() {
        assertEquals(1, estimator.estimateComplexity(List.of()));
    }

    @Test
    void singleShortMessageHasMinimumComplexity() {
        Msg msg = Msg.of(MsgRole.USER, "hello");
        int score = estimator.estimateComplexity(List.of(msg));
        assertEquals(1, score);
    }

    @Test
    void tenMessagesAddsOneToScore() {
        List<Msg> msgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            msgs.add(Msg.of(MsgRole.USER, "msg " + i));
        }
        int score = estimator.estimateComplexity(msgs);
        // base=1, message factor=min(3,10/10)=1 → at least 2
        assertTrue(score >= 2);
    }

    @Test
    void codeBlockInRecentMessageAddsTwo() {
        // Single message with code block — base 1 + code factor 2 = 3
        Msg msg = Msg.of(MsgRole.USER, "Here is code:\n```java\nint x = 1;\n```");
        int score = estimator.estimateComplexity(List.of(msg));
        assertEquals(3, score);
    }

    @Test
    void complexityKeywordInLastUserMessageAddsTwo() {
        Msg msg = Msg.of(MsgRole.USER, "Please help me debug this issue");
        int score = estimator.estimateComplexity(List.of(msg));
        // base=1, keyword factor=2 → 3
        assertEquals(3, score);
    }

    @Test
    void multipleFactorsCombine() {
        // code + keyword in single message: base=1, code=+2, keyword=+2 → 5
        Msg msg = Msg.of(MsgRole.USER, "Please debug this:\n```python\nprint(x)\n```");
        int score = estimator.estimateComplexity(List.of(msg));
        assertEquals(5, score);
    }

    @Test
    void scoreIsClampedToMaxTen() {
        List<Msg> msgs = new ArrayList<>();
        // 30 messages → message factor = min(3, 30/10) = 3
        for (int i = 0; i < 30; i++) {
            msgs.add(
                    Msg.of(
                            MsgRole.USER,
                            "refactor and optimize the architecture design:\n```\ncode\n```"));
        }
        int score = estimator.estimateComplexity(msgs);
        assertTrue(score <= 10);
    }

    @Test
    void scoreIsAtLeastOne() {
        Msg msg = Msg.of(MsgRole.USER, "hi");
        int score = estimator.estimateComplexity(List.of(msg));
        assertTrue(score >= 1);
    }

    @Test
    void thinkingBudgetZeroWhenThinkingNotSupported() {
        ModelCapability cap =
                new ModelCapability(
                        "claude",
                        "haiku",
                        100_000,
                        8192,
                        false,
                        true,
                        ToolVerbosity.STANDARD,
                        null);
        assertEquals(0, estimator.thinkingBudget(cap, 5));
    }

    @Test
    void thinkingBudgetZeroWhenRangeIsNull() {
        ModelCapability cap =
                new ModelCapability(
                        "claude", "haiku", 100_000, 8192, true, true, ToolVerbosity.STANDARD, null);
        assertEquals(0, estimator.thinkingBudget(cap, 5));
    }

    @Test
    void thinkingBudgetMinForComplexityOne() {
        // complexity=1, t=(1-1)/9=0.0 → lerp(0.0) = min = 1000
        ModelCapability cap =
                new ModelCapability(
                        "claude",
                        "sonnet",
                        100_000,
                        8192,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        new IntRange(1000, 10000));
        assertEquals(1000, estimator.thinkingBudget(cap, 1));
    }

    @Test
    void thinkingBudgetMaxForComplexityTen() {
        // complexity=10, t=(10-1)/9=1.0 → lerp(1.0) = max = 10000
        ModelCapability cap =
                new ModelCapability(
                        "claude",
                        "sonnet",
                        100_000,
                        8192,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        new IntRange(1000, 10000));
        assertEquals(10000, estimator.thinkingBudget(cap, 10));
    }

    @Test
    void thinkingBudgetInterpolatedForMidComplexity() {
        // complexity=5, t=4/9≈0.444 → lerp = 1000 + 0.444*(10000-1000) ≈ 5000
        ModelCapability cap =
                new ModelCapability(
                        "claude",
                        "sonnet",
                        100_000,
                        8192,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        new IntRange(1000, 10000));
        int budget = estimator.thinkingBudget(cap, 5);
        assertTrue(budget >= 1000 && budget <= 10000);
    }
}
