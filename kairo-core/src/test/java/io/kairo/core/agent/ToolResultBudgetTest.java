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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.context.TokenBudgetManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultBudgetTest {

    @Test
    void shouldTruncateOversizedToolResultAndAnnotateMetadata() {
        TokenBudgetManager budgetManager = new TokenBudgetManager(20_000, 4_000);
        List<Msg> history = List.of(Msg.of(MsgRole.USER, "run diagnostics"));

        String oversized = "A".repeat(20_000);
        ToolResult original = new ToolResult("tc-1", oversized, false, Map.of("source", "bash"));

        ToolResultBudget.AppliedResult applied =
                ToolResultBudget.apply(List.of(original), history, budgetManager);

        assertEquals(1, applied.truncatedCount());
        assertTrue(applied.originalTokens() > applied.keptTokens());
        ToolResult adjusted = applied.results().get(0);
        assertNotEquals(oversized, adjusted.content());
        assertTrue(adjusted.content().contains("truncated by ToolResultBudget"));
        assertEquals("bash", adjusted.metadata().get("source"));
        assertEquals(true, adjusted.metadata().get("tool_result_budget_applied"));
        assertEquals(true, adjusted.metadata().get("tool_result_truncated"));
        assertEquals(
                "exceeds_per_result_budget", adjusted.metadata().get("tool_result_budget_reason"));
    }

    @Test
    void shouldKeepSmallToolResultWithoutTruncation() {
        TokenBudgetManager budgetManager = new TokenBudgetManager(20_000, 4_000);
        List<Msg> history = List.of(Msg.of(MsgRole.USER, "ping"));
        ToolResult original = new ToolResult("tc-2", "ok", false, Map.of());

        ToolResultBudget.AppliedResult applied =
                ToolResultBudget.apply(List.of(original), history, budgetManager);

        assertEquals(0, applied.truncatedCount());
        ToolResult adjusted = applied.results().get(0);
        assertEquals("ok", adjusted.content());
        assertEquals(false, adjusted.metadata().get("tool_result_truncated"));
        assertEquals(
                "within_per_result_budget", adjusted.metadata().get("tool_result_budget_reason"));
    }
}
