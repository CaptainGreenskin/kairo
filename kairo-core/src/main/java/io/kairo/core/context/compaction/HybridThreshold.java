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
package io.kairo.core.context.compaction;

import io.kairo.api.context.ContextState;

/**
 * Hybrid threshold calculation for compaction triggers.
 *
 * <p>Supports both percentage-based and absolute token buffer thresholds. The effective threshold
 * is the minimum of the two: {@code min(percentageThreshold * budget, budget - absoluteBuffer)}.
 *
 * <p>When no context window information is available (contextWindow == 0), falls back to the
 * percentage-based threshold only.
 */
final class HybridThreshold {

    private HybridThreshold() {}

    /**
     * Determine if a compaction stage should trigger.
     *
     * @param state the current context state
     * @param percentageThreshold the percentage-based trigger threshold (e.g. 0.80)
     * @param absoluteBuffer the absolute token buffer (e.g. 40000)
     * @return true if compaction should trigger
     */
    static boolean shouldTrigger(
            ContextState state, float percentageThreshold, int absoluteBuffer) {
        int contextWindow = state.contextWindow();
        if (contextWindow <= 0) {
            // Fallback: percentage-only when no context window is known
            return state.pressure() >= percentageThreshold;
        }

        // Effective threshold = min(percentage * budget, budget - absoluteBuffer)
        float percentageTrigger = percentageThreshold * contextWindow;
        float absoluteTrigger = contextWindow - absoluteBuffer;
        float effectiveThreshold = Math.min(percentageTrigger, absoluteTrigger);

        // usedTokens may not always be set; if zero, derive from pressure
        int usedTokens = state.usedTokens();
        if (usedTokens <= 0 && state.pressure() > 0) {
            usedTokens = (int) (state.pressure() * contextWindow);
        }

        return usedTokens >= effectiveThreshold;
    }
}
