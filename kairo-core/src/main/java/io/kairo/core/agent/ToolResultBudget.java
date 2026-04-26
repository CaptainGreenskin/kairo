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
package io.kairo.core.agent;

import io.kairo.api.message.Msg;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.context.TokenBudgetManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a bounded token budget to tool results before they are appended into conversation
 * history.
 *
 * <p>This keeps tool outputs from monopolizing context space while preserving enough signal for the
 * next reasoning turn.
 */
final class ToolResultBudget {

    // Tool result content is generally denser than plain text; use ~4 chars/token heuristic.
    private static final int CHARS_PER_TOKEN = 4;
    private static final int MIN_TOTAL_BUDGET_TOKENS = 256;
    private static final int MAX_TOTAL_BUDGET_TOKENS = 8192;
    private static final int MIN_PER_RESULT_TOKENS = 96;
    private static final int MAX_PER_RESULT_TOKENS = 2048;
    private static final double FRACTION_OF_REMAINING_BUDGET = 0.35d;

    private ToolResultBudget() {}

    static AppliedResult apply(
            List<ToolResult> results, List<Msg> history, TokenBudgetManager tokenBudgetManager) {
        if (results == null || results.isEmpty()) {
            return new AppliedResult(List.of(), 0, 0, 0, 0, 0);
        }

        int remainingBudget = Math.max(0, tokenBudgetManager.getRemainingBudget(history));
        int totalBudgetTokens = boundedTotalBudget(remainingBudget);
        int perResultBudgetTokens = boundedPerResultBudget(totalBudgetTokens, results.size());

        List<ToolResult> normalized = new ArrayList<>(results.size());
        int truncatedCount = 0;
        int originalTokens = 0;
        int keptTokens = 0;

        for (ToolResult result : results) {
            BudgetedContent budgeted = budgetContent(result.content(), perResultBudgetTokens);
            if (budgeted.truncated()) {
                truncatedCount++;
            }
            originalTokens += budgeted.originalTokens();
            keptTokens += budgeted.keptTokens();
            normalized.add(withBudgetMetadata(result, budgeted));
        }

        return new AppliedResult(
                normalized,
                truncatedCount,
                originalTokens,
                keptTokens,
                remainingBudget,
                perResultBudgetTokens);
    }

    private static ToolResult withBudgetMetadata(ToolResult result, BudgetedContent budgeted) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result.metadata() != null && !result.metadata().isEmpty()) {
            metadata.putAll(result.metadata());
        }
        metadata.put("tool_result_budget_applied", true);
        metadata.put("tool_result_original_tokens", budgeted.originalTokens());
        metadata.put("tool_result_kept_tokens", budgeted.keptTokens());
        metadata.put("tool_result_truncated", budgeted.truncated());
        metadata.put("tool_result_budget_reason", budgeted.reason());
        return new ToolResult(
                result.toolUseId(), budgeted.content(), result.isError(), Map.copyOf(metadata));
    }

    private static BudgetedContent budgetContent(String content, int perResultBudgetTokens) {
        String safe = content == null ? "" : content;
        int originalTokens = estimateTokens(safe);
        if (originalTokens <= perResultBudgetTokens) {
            return new BudgetedContent(
                    safe, originalTokens, originalTokens, false, "within_per_result_budget");
        }

        int keepChars = Math.max(0, perResultBudgetTokens * CHARS_PER_TOKEN);
        String truncated = safe.substring(0, Math.min(keepChars, safe.length()));
        String suffix =
                "\n...[truncated by ToolResultBudget: originalTokens="
                        + originalTokens
                        + ", keptTokens="
                        + perResultBudgetTokens
                        + "]";
        String finalContent = truncated + suffix;
        int keptTokens = estimateTokens(finalContent);
        return new BudgetedContent(
                finalContent, originalTokens, keptTokens, true, "exceeds_per_result_budget");
    }

    private static int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 1;
        }
        return Math.max(1, content.length() / CHARS_PER_TOKEN);
    }

    private static int boundedTotalBudget(int remainingBudgetTokens) {
        int candidate = (int) Math.floor(remainingBudgetTokens * FRACTION_OF_REMAINING_BUDGET);
        if (candidate <= 0) {
            return MIN_TOTAL_BUDGET_TOKENS;
        }
        return Math.min(MAX_TOTAL_BUDGET_TOKENS, Math.max(MIN_TOTAL_BUDGET_TOKENS, candidate));
    }

    private static int boundedPerResultBudget(int totalBudgetTokens, int resultCount) {
        int resultSlots = Math.max(1, resultCount);
        int candidate = totalBudgetTokens / resultSlots;
        return Math.min(MAX_PER_RESULT_TOKENS, Math.max(MIN_PER_RESULT_TOKENS, candidate));
    }

    record AppliedResult(
            List<ToolResult> results,
            int truncatedCount,
            int originalTokens,
            int keptTokens,
            int remainingBudgetTokens,
            int perResultBudgetTokens) {}

    private record BudgetedContent(
            String content, int originalTokens, int keptTokens, boolean truncated, String reason) {}
}
