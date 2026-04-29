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

/**
 * Resolves thinking token budget based on request complexity.
 *
 * <p>Maps a complexity score [1-10] to three discrete tiers:
 *
 * <ul>
 *   <li><b>LOW</b> (complexity 1-3): simple queries — default 4,000 tokens
 *   <li><b>MED</b> (complexity 4-6): standard tasks — default 8,000 tokens
 *   <li><b>HIGH</b> (complexity 7-10): complex reasoning — default 16,000 tokens
 * </ul>
 *
 * <p>Tier defaults are configurable via {@code KAIRO_THINKING_BUDGET_LOW}, {@code
 * KAIRO_THINKING_BUDGET_MED}, and {@code KAIRO_THINKING_BUDGET_HIGH} environment variables.
 */
public final class ThinkingBudgetResolver {

    private static final int DEFAULT_LOW = 4_000;
    private static final int DEFAULT_MED = 8_000;
    private static final int DEFAULT_HIGH = 16_000;

    private static final int BUDGET_LOW = resolveEnv("KAIRO_THINKING_BUDGET_LOW", DEFAULT_LOW);
    private static final int BUDGET_MED = resolveEnv("KAIRO_THINKING_BUDGET_MED", DEFAULT_MED);
    private static final int BUDGET_HIGH = resolveEnv("KAIRO_THINKING_BUDGET_HIGH", DEFAULT_HIGH);

    private ThinkingBudgetResolver() {}

    /**
     * Returns thinking budget tokens for the given complexity score [1, 10].
     *
     * @param complexityScore the complexity score from {@link ComplexityEstimator}
     * @return the resolved thinking budget in tokens
     */
    public static int resolve(int complexityScore) {
        if (complexityScore <= 3) {
            return BUDGET_LOW;
        }
        if (complexityScore <= 6) {
            return BUDGET_MED;
        }
        return BUDGET_HIGH;
    }

    private static int resolveEnv(String name, int defaultValue) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
