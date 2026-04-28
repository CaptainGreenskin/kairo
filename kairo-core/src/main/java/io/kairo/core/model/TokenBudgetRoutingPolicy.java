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

import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Routes to the cheapest tier that can handle the current context size.
 *
 * <p>Thresholds (approximate token counts using chars/4 heuristic):
 *
 * <ul>
 *   <li>&lt; {@link #FREE_THRESHOLD} tokens → {@link ModelCostTier#FREE}
 *   <li>&lt; {@link #STANDARD_THRESHOLD} tokens → {@link ModelCostTier#STANDARD}
 *   <li>otherwise → {@link ModelCostTier#PREMIUM}
 * </ul>
 */
public final class TokenBudgetRoutingPolicy {

    public static final int FREE_THRESHOLD = 2000;
    public static final int STANDARD_THRESHOLD = 8000;

    private static final int CHARS_PER_TOKEN = 4;

    private TokenBudgetRoutingPolicy() {}

    /**
     * Select the cost tier based on estimated context token count.
     *
     * @param messages the conversation history to estimate size from
     * @return the selected tier
     */
    public static ModelCostTier select(List<Msg> messages) {
        int estimatedTokens = estimateTokens(messages);
        if (estimatedTokens < FREE_THRESHOLD) return ModelCostTier.FREE;
        if (estimatedTokens < STANDARD_THRESHOLD) return ModelCostTier.STANDARD;
        return ModelCostTier.PREMIUM;
    }

    static int estimateTokens(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int totalChars = 0;
        for (Msg msg : messages) {
            String text = msg.text();
            if (text != null) {
                totalChars += text.length();
            }
        }
        return totalChars / CHARS_PER_TOKEN;
    }
}
