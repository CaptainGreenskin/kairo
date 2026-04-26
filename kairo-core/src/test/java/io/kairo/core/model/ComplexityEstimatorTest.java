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
package io.kairo.core.model;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.IntRange;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.core.model.anthropic.ComplexityEstimator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComplexityEstimatorTest {

    private ComplexityEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new ComplexityEstimator();
    }

    @Test
    @DisplayName("Empty messages returns minimum complexity (1)")
    void emptyMessagesReturnMinComplexity() {
        assertEquals(1, estimator.estimateComplexity(List.of()));
        assertEquals(1, estimator.estimateComplexity(null));
    }

    @Test
    @DisplayName("Few simple messages result in low complexity")
    void fewSimpleMessagesLowComplexity() {
        List<Msg> msgs =
                List.of(Msg.of(MsgRole.USER, "Hello"), Msg.of(MsgRole.ASSISTANT, "Hi there!"));
        int complexity = estimator.estimateComplexity(msgs);
        assertTrue(
                complexity >= 1 && complexity <= 3, "Expected low complexity, got " + complexity);
    }

    @Test
    @DisplayName("Many messages increase complexity via message count factor")
    void manyMessagesIncreasesComplexity() {
        List<Msg> msgs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            msgs.add(Msg.of(MsgRole.USER, "message " + i));
        }
        int complexity = estimator.estimateComplexity(msgs);
        assertTrue(
                complexity >= 4, "Expected higher complexity with 30 messages, got " + complexity);
    }

    @Test
    @DisplayName("Code blocks in recent messages increase complexity")
    void codeBlocksIncreaseComplexity() {
        List<Msg> withCode =
                List.of(
                        Msg.of(MsgRole.USER, "Fix this"),
                        Msg.of(MsgRole.ASSISTANT, "Here is the fix:\n```java\nclass Foo {}\n```"));
        List<Msg> withoutCode =
                List.of(Msg.of(MsgRole.USER, "Fix this"), Msg.of(MsgRole.ASSISTANT, "Done!"));

        int withCodeComplexity = estimator.estimateComplexity(withCode);
        int withoutCodeComplexity = estimator.estimateComplexity(withoutCode);
        assertTrue(
                withCodeComplexity > withoutCodeComplexity,
                "Code blocks should increase complexity");
    }

    @Test
    @DisplayName("Complex keywords in user message increase complexity")
    void complexKeywordsIncreaseComplexity() {
        List<Msg> msgs =
                List.of(Msg.of(MsgRole.USER, "Please debug and refactor this architecture"));
        int complexity = estimator.estimateComplexity(msgs);
        assertTrue(complexity >= 3, "Expected higher complexity with keywords, got " + complexity);
    }

    @Test
    @DisplayName("Complexity never exceeds 10")
    void complexityNeverExceedsTen() {
        // Create worst case: many messages, code blocks, keywords, tool calls
        List<Msg> msgs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            msgs.add(
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .addContent(new Content.ToolUseContent("t" + i, "tool", Map.of()))
                            .build());
        }
        msgs.add(Msg.of(MsgRole.USER, "debug refactor architect ```code``` complex optimize"));
        int complexity = estimator.estimateComplexity(msgs);
        assertTrue(complexity <= 10, "Complexity must not exceed 10, got " + complexity);
    }

    @Test
    @DisplayName("Complexity never below 1")
    void complexityNeverBelowOne() {
        assertEquals(1, estimator.estimateComplexity(List.of()));
        assertEquals(1, estimator.estimateComplexity(null));
    }

    @Test
    @DisplayName("Thinking budget interpolation: complexity 1 → min, complexity 10 → max")
    void thinkingBudgetCalculation() {
        ModelCapability cap =
                new ModelCapability(
                        "claude",
                        "sonnet",
                        200_000,
                        8192,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        new IntRange(2048, 16384));

        int budgetMin = estimator.thinkingBudget(cap, 1);
        int budgetMax = estimator.thinkingBudget(cap, 10);

        assertEquals(2048, budgetMin, "Complexity 1 should map to min budget");
        assertEquals(16384, budgetMax, "Complexity 10 should map to max budget");

        // Mid-range complexity should be between min and max
        int budgetMid = estimator.thinkingBudget(cap, 5);
        assertTrue(budgetMid > 2048 && budgetMid < 16384);
    }

    @Test
    @DisplayName("Thinking budget is 0 when model does not support thinking")
    void thinkingBudgetZeroWhenNotSupported() {
        ModelCapability cap =
                new ModelCapability(
                        "gpt", "4o", 128_000, 16384, false, false, ToolVerbosity.STANDARD, null);
        assertEquals(0, estimator.thinkingBudget(cap, 5));
    }
}
