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

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ModelRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenBudgetManagerV2Test {

    @Test
    @DisplayName("forModel factory creates manager with correct budget from ModelRegistry")
    void testForModelFactory() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        ModelRegistry.ModelSpec spec = mgr.getModelSpec();
        assertEquals(200_000, spec.contextWindow());
        assertEquals(20_000, spec.maxOutputTokens());
    }

    @Test
    @DisplayName("getEffectiveBudget = contextWindow - maxOutput - 13000 buffer")
    void testGetEffectiveBudget() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        // 200_000 - 20_000 - 13_000 = 167_000
        assertEquals(167_000, mgr.getEffectiveBudget());
    }

    @Test
    @DisplayName("getEffectiveBudget for gpt-4o")
    void testGetEffectiveBudgetGpt4o() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("gpt-4o");

        // 128_000 - 16_384 - 13_000 = 98_616
        assertEquals(98_616, mgr.getEffectiveBudget());
    }

    @Test
    @DisplayName("estimateTokens with fresh API usage returns inputTokens")
    void testEstimateTokensWithFreshApiUsage() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        // Simulate API usage feedback on current turn
        ModelResponse.Usage usage = new ModelResponse.Usage(50_000, 1_000, 0, 0);
        mgr.updateFromApiUsage(usage);

        List<Msg> messages =
                List.of(Msg.of(MsgRole.USER, "Hello world"), Msg.of(MsgRole.ASSISTANT, "Hi there"));

        // Should return the API-reported inputTokens since usage is fresh (same turn)
        assertEquals(50_000, mgr.estimateTokens(messages));
    }

    @Test
    @DisplayName("estimateTokens with stale usage falls back to char-based estimate")
    void testEstimateTokensFallbackStaleUsage() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        // Record API usage on turn 0
        ModelResponse.Usage usage = new ModelResponse.Usage(50_000, 1_000, 0, 0);
        mgr.updateFromApiUsage(usage);

        // Advance turn — now the API usage is stale
        mgr.advanceTurn();

        String text = "a".repeat(300);
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, text));

        // Fallback: totalChars * 4 / 3 = 300 * 4 / 3 = 400
        assertEquals(400, mgr.estimateTokens(messages));
    }

    @Test
    @DisplayName("estimateTokens with no API usage falls back to char-based estimate")
    void testEstimateTokensNoApiUsage() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        String text = "a".repeat(120);
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, text));

        // chars * 4 / 3 = 120 * 4 / 3 = 160
        assertEquals(160, mgr.estimateTokens(messages));
    }

    @Test
    @DisplayName("getPressure calculation based on messages")
    void testGetPressure() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        // effective budget = 167_000
        // Create messages whose char-based estimate equals the budget
        String text = "a".repeat(1000);
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, text));

        double pressure = mgr.getPressure(messages);
        // estimated tokens = 1000 * 4 / 3 = 1333
        // pressure = 1333 / 167000 ≈ 0.00798
        double expectedPressure = (1000.0 * 4 / 3) / 167_000;
        assertEquals(expectedPressure, pressure, 0.001);
    }

    @Test
    @DisplayName("getRemainingBudget = effectiveBudget - estimatedTokens")
    void testGetRemainingBudget() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        String text = "a".repeat(300);
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, text));

        // effective = 167_000, estimated = 300 * 4 / 3 = 400
        assertEquals(167_000 - 400, mgr.getRemainingBudget(messages));
    }

    @Test
    @DisplayName("advanceTurn increments turn and makes API usage stale")
    void testAdvanceTurn() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");

        // Fresh usage on turn 0
        mgr.updateFromApiUsage(new ModelResponse.Usage(42_000, 500, 0, 0));

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "test"));
        assertEquals(42_000, mgr.estimateTokens(messages));

        // Advance turn — usage becomes stale
        mgr.advanceTurn();
        int fallback = mgr.estimateTokens(messages);
        // Should now use char-based: "test".length() * 4 / 3 = 4 * 4 / 3 = 5
        assertEquals("test".length() * 4 / 3, fallback);
    }

    @Test
    @DisplayName("forModel with unknown model uses defaults")
    void testForModelUnknown() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("unknown-model");

        ModelRegistry.ModelSpec spec = mgr.getModelSpec();
        assertEquals(128_000, spec.contextWindow());
        assertEquals(8_192, spec.maxOutputTokens());

        // effective = 128_000 - 8_192 - 13_000 = 106_808
        assertEquals(106_808, mgr.getEffectiveBudget());
    }

    @Test
    @DisplayName("getPressure returns 1.0 when effective budget is zero")
    void testGetPressureZeroBudget() {
        // Create a manager where contextWindow - maxOutput - buffer <= 0
        TokenBudgetManager mgr = new TokenBudgetManager(10_000, 10_000);
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "test"));

        // effective = 10_000 - 10_000 - 13_000 = -13_000 => <= 0, return 1.0
        assertEquals(1.0, mgr.getPressure(messages), 0.001);
    }

    @Test
    @DisplayName("custom TokenEstimator is used when API usage is stale")
    void testCustomTokenEstimatorFallback() {
        TokenBudgetManager mgr = new TokenBudgetManager("claude-sonnet-4-20250514", messages -> 42);
        mgr.updateFromApiUsage(new ModelResponse.Usage(100, 10, 0, 0));
        assertEquals(100, mgr.estimateTokens(List.of(Msg.of(MsgRole.USER, "fresh"))));

        mgr.advanceTurn();
        assertEquals(42, mgr.estimateTokens(List.of(Msg.of(MsgRole.USER, "stale"))));
    }

    @Test
    @DisplayName("recordModelUsage updates unified accounted tokens once per turn")
    void testRecordModelUsageSingleSourceAccounting() {
        TokenBudgetManager mgr = TokenBudgetManager.forModel("claude-sonnet-4-20250514");
        ModelResponse.Usage usage = new ModelResponse.Usage(120, 30, 0, 0);

        mgr.recordModelUsage(usage);
        mgr.recordModelUsage(usage);

        assertEquals(150, mgr.totalAccountedTokens());
        assertEquals(150, mgr.used());

        mgr.advanceTurn();
        mgr.recordModelUsage(new ModelResponse.Usage(10, 5, 0, 0));
        assertEquals(165, mgr.totalAccountedTokens());
    }
}
