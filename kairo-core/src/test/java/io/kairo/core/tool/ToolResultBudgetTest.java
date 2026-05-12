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
package io.kairo.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultBudgetTest {

    // ===== Below Budget: no truncation =====

    @Test
    void applyResultBudget_contentBelowLimit_notTruncated() {
        String content = "short output";
        ToolResult result = ToolResult.success("tool1", content);

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertSame(result, budgeted);
        assertEquals(content, budgeted.content());
    }

    @Test
    void applyResultBudget_contentExactlyAtLimit_notTruncated() {
        // Build content exactly at the default limit (20_000)
        String content = "x".repeat(20_000);
        ToolResult result = ToolResult.success("tool1", content);

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertSame(result, budgeted);
        assertEquals(20_000, budgeted.content().length());
    }

    // ===== Above Budget: compression =====

    @Test
    void applyResultBudget_contentExceedsLimit_compressedWithTailExtract() {
        String content = "x".repeat(30_000);
        ToolResult result = ToolResult.success("tool1", content);

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertNotSame(result, budgeted);
        assertTrue(budgeted.content().startsWith("x".repeat(2_000)));
        assertTrue(budgeted.content().contains("chars omitted (middle)"));
        assertTrue(budgeted.content().endsWith("x".repeat(3_000)));
        assertFalse(budgeted.isError());
    }

    @Test
    void applyResultBudget_compressedContentLength_withinBudgetPlusMarker() {
        String content = "a".repeat(50_000);
        ToolResult result = ToolResult.success("tool1", content);

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        // Compressed length should be well under max + small marker overhead
        assertTrue(budgeted.content().length() < 20_000 + 100);
    }

    // ===== Null and edge cases =====

    @Test
    void applyResultBudget_nullResult_returnsNull() {
        assertNull(DefaultToolExecutor.applyResultBudget(null));
    }

    @Test
    void applyResultBudget_nullContent_notTruncated() {
        ToolResult result = ToolResult.success("tool1", null);

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertSame(result, budgeted);
        assertNull(budgeted.content());
    }

    @Test
    void applyResultBudget_emptyContent_notTruncated() {
        ToolResult result = ToolResult.success("tool1", "");

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertSame(result, budgeted);
        assertEquals("", budgeted.content());
    }

    // ===== Error results =====

    @Test
    void applyResultBudget_errorResult_compressedSameAsNormal() {
        String content = "e".repeat(40_000);
        ToolResult result = ToolResult.error("tool1", content);

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertNotSame(result, budgeted);
        assertTrue(budgeted.isError());
        assertTrue(budgeted.content().contains("chars omitted (middle)"));
    }

    // ===== Metadata preservation =====

    @Test
    void applyResultBudget_truncated_preservesMetadata() {
        String content = "z".repeat(25_000);
        ToolResult result = ToolResult.success("tool1", content, Map.of("exit_code", 0));

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertEquals(0, budgeted.metadata().get("exit_code"));
    }

    @Test
    void applyResultBudget_truncated_preservesToolUseId() {
        String content = "y".repeat(25_000);
        ToolResult result = ToolResult.success("my-tool-123", content);

        ToolResult budgeted = DefaultToolExecutor.applyResultBudget(result);

        assertEquals("my-tool-123", budgeted.toolUseId());
    }
}
