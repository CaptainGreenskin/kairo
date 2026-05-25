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

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Static per-million-token pricing table for model providers Kairo invokes.
 *
 * <p>Used by {@link io.kairo.core.tracing.TracingModelProvider} to populate {@code
 * langfuse.cost_details} so the Langfuse dashboard cost column is non-zero. Langfuse can also
 * compute cost on its own if {@code model} is registered in its model library, but populating cost
 * client-side is the only way to guarantee parity when running against a self-hosted Langfuse with
 * an incomplete model catalog.
 *
 * <p>Prices are expressed as USD per million tokens. CNY-denominated providers (GLM) are converted
 * at a fixed reference rate documented in {@link #CNY_USD_RATE} — exact precision is not the goal,
 * showing cost-of-order-of-magnitude in the dashboard is.
 *
 * <p>Match is prefix-based on the canonical model name (lower-cased) so that {@code glm-5.1} and
 * {@code glm-5.1-preview} both pick up the same entry. Unknown models return {@link
 * Optional#empty()} so cost is silently omitted rather than guessed.
 */
public final class ModelPricing {

    /** Reference CNY→USD rate used for GLM pricing conversion. Updated periodically. */
    public static final double CNY_USD_RATE = 0.14;

    private record Price(double inputPerMillionUsd, double outputPerMillionUsd) {}

    // Lower-cased model-name prefix → price. Longest prefix wins (entries iterated in declared
    // order so list specific variants before family defaults).
    private static final Map<String, Price> PRICES =
            Map.ofEntries(
                    // ---- Zhipu GLM (CNY → USD via CNY_USD_RATE) ----
                    // glm-5.1: ¥4 input / ¥16 output per 1M tokens (2026 dogfood rate)
                    Map.entry("glm-5.1", new Price(4.0 * CNY_USD_RATE, 16.0 * CNY_USD_RATE)),
                    Map.entry("glm-4.5", new Price(4.0 * CNY_USD_RATE, 16.0 * CNY_USD_RATE)),
                    Map.entry("glm-4", new Price(1.0 * CNY_USD_RATE, 1.0 * CNY_USD_RATE)),

                    // ---- OpenAI ----
                    Map.entry("gpt-5", new Price(2.50, 10.00)),
                    Map.entry("gpt-4o-mini", new Price(0.15, 0.60)),
                    Map.entry("gpt-4o", new Price(2.50, 10.00)),
                    Map.entry("gpt-4-turbo", new Price(10.00, 30.00)),
                    Map.entry("gpt-4", new Price(30.00, 60.00)),
                    Map.entry("gpt-3.5", new Price(0.50, 1.50)),

                    // ---- Anthropic ----
                    Map.entry("claude-opus-4", new Price(15.00, 75.00)),
                    Map.entry("claude-sonnet-4", new Price(3.00, 15.00)),
                    Map.entry("claude-haiku-4", new Price(0.80, 4.00)),
                    Map.entry("claude-3-7-sonnet", new Price(3.00, 15.00)),
                    Map.entry("claude-3-5-sonnet", new Price(3.00, 15.00)),
                    Map.entry("claude-3-5-haiku", new Price(0.80, 4.00)),

                    // ---- Google Gemini ----
                    Map.entry("gemini-2.5-pro", new Price(1.25, 5.00)),
                    Map.entry("gemini-2.5-flash", new Price(0.075, 0.30)),
                    Map.entry("gemini-2.0-flash", new Price(0.10, 0.40)));

    private ModelPricing() {}

    /**
     * Compute total cost in USD for a single invocation.
     *
     * @param modelName canonical model identifier (case-insensitive)
     * @param inputTokens prompt tokens charged
     * @param outputTokens completion tokens charged
     * @return total cost USD, or empty if the model is not in the pricing table or token counts are
     *     all zero
     */
    public static Optional<Double> estimateUsd(
            String modelName, long inputTokens, long outputTokens) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        if (inputTokens <= 0 && outputTokens <= 0) {
            return Optional.empty();
        }
        Price price = lookup(modelName);
        if (price == null) {
            return Optional.empty();
        }
        double cost =
                (inputTokens * price.inputPerMillionUsd + outputTokens * price.outputPerMillionUsd)
                        / 1_000_000.0;
        return Optional.of(cost);
    }

    private static Price lookup(String modelName) {
        String normalized = modelName.toLowerCase(Locale.ROOT);
        String bestKey = null;
        for (String key : PRICES.keySet()) {
            if (normalized.startsWith(key)
                    && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        return bestKey == null ? null : PRICES.get(bestKey);
    }
}
