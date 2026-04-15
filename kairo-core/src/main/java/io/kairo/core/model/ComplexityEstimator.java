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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelCapability;
import java.util.List;

/**
 * Estimates conversation complexity for dynamic thinking budget allocation.
 *
 * <p>Uses four heuristic factors to produce a complexity score from 1 (trivial) to 10 (highly
 * complex):
 *
 * <ol>
 *   <li><b>Message count</b> — more messages indicate richer context
 *   <li><b>Tool call density</b> — frequent tool use signals multi-step reasoning
 *   <li><b>Code block presence</b> — code in recent messages suggests technical work
 *   <li><b>Question complexity</b> — keywords in the last user message hint at difficulty
 * </ol>
 */
public class ComplexityEstimator {

    /**
     * Estimate conversation complexity on a 1–10 scale.
     *
     * @param messages the conversation history
     * @return complexity score between 1 and 10
     */
    public int estimateComplexity(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return 1;
        }

        int score = 1;

        // Factor 1: Message count (more messages = more complex context)
        score += Math.min(3, messages.size() / 10);

        // Factor 2: Tool call density
        long toolCalls =
                messages.stream()
                        .filter(m -> m.role() == MsgRole.ASSISTANT)
                        .flatMap(m -> m.contents().stream())
                        .filter(Content.ToolUseContent.class::isInstance)
                        .count();
        score += Math.min(2, (int) (toolCalls / 5));

        // Factor 3: Code block presence in recent messages (last 5)
        List<Msg> recent = messages.subList(Math.max(0, messages.size() - 5), messages.size());
        boolean hasCode =
                recent.stream()
                        .anyMatch(
                                m -> {
                                    String text = m.text();
                                    return text != null
                                            && (text.contains("```")
                                                    || text.contains("class ")
                                                    || text.contains("function "));
                                });
        if (hasCode) {
            score += 2;
        }

        // Factor 4: Question complexity (keywords in last user message)
        Msg lastUser = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == MsgRole.USER) {
                lastUser = messages.get(i);
                break;
            }
        }
        if (lastUser != null) {
            String text = lastUser.text();
            if (text != null) {
                String lower = text.toLowerCase();
                if (lower.contains("debug")
                        || lower.contains("refactor")
                        || lower.contains("architect")
                        || lower.contains("design")
                        || lower.contains("optimize")
                        || lower.contains("complex")) {
                    score += 2;
                }
            }
        }

        return Math.min(10, Math.max(1, score));
    }

    /**
     * Calculate the thinking budget based on conversation complexity and model capability.
     *
     * <p>Returns 0 if the model does not support thinking or has no budget range configured.
     * Otherwise, linearly interpolates: complexity 1 maps to the minimum budget, complexity 10 maps
     * to the maximum.
     *
     * @param capability the model capability
     * @param complexity the complexity score (1–10)
     * @return the thinking budget in tokens, or 0 if thinking is not supported
     */
    public int thinkingBudget(ModelCapability capability, int complexity) {
        if (!capability.supportsThinking() || capability.thinkingBudgetRange() == null) {
            return 0;
        }
        // Linear interpolation: complexity 1 → min budget, complexity 10 → max budget
        return capability.thinkingBudgetRange().lerp((complexity - 1) / 9.0);
    }
}
