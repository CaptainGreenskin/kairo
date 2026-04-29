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

import io.kairo.api.message.Content;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolPartitionerTest {

    private static final ToolSideEffect resolver(String name) {
        return switch (name) {
            case "read", "glob", "grep", "web_fetch" -> ToolSideEffect.READ_ONLY;
            case "bash", "edit", "write", "notebook_edit", "file_delete" -> ToolSideEffect.WRITE;
            default -> ToolSideEffect.SYSTEM_CHANGE;
        };
    }

    private static Content.ToolUseContent toolUse(String id, String name) {
        return new Content.ToolUseContent(id, name, Map.of());
    }

    // ================================
    //  Partition tests
    // ================================

    @Test
    void bashGoesToSerial() {
        var toolUses = List.of(toolUse("u1", "bash"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);
        assertTrue(partition.parallel().isEmpty());
        assertEquals(1, partition.serial().size());
        assertEquals("bash", partition.serial().get(0).toolName());
    }

    @Test
    void readOnlyToolsGoToParallel() {
        var toolUses = List.of(toolUse("u1", "read"), toolUse("u2", "glob"), toolUse("u3", "grep"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);
        assertEquals(3, partition.parallel().size());
        assertTrue(partition.serial().isEmpty());
        assertEquals("read", partition.parallel().get(0).toolName());
        assertEquals("glob", partition.parallel().get(1).toolName());
        assertEquals("grep", partition.parallel().get(2).toolName());
    }

    @Test
    void mixedBatch_correctGrouping() {
        var toolUses =
                List.of(
                        toolUse("u1", "read"),
                        toolUse("u2", "bash"),
                        toolUse("u3", "glob"),
                        toolUse("u4", "write"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);
        assertEquals(2, partition.parallel().size());
        assertEquals(2, partition.serial().size());
        // Parallel group preserves relative order
        assertEquals("read", partition.parallel().get(0).toolName());
        assertEquals("glob", partition.parallel().get(1).toolName());
        // Serial group preserves relative order
        assertEquals("bash", partition.serial().get(0).toolName());
        assertEquals("write", partition.serial().get(1).toolName());
    }

    @Test
    void emptyBatch_returnsEmptyPartition() {
        var partition = ToolPartitioner.partition(List.of(), ToolPartitionerTest::resolver);
        assertTrue(partition.parallel().isEmpty());
        assertTrue(partition.serial().isEmpty());
    }

    @Test
    void singleWriteTool_serialOnly() {
        var toolUses = List.of(toolUse("u1", "edit"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);
        assertTrue(partition.parallel().isEmpty());
        assertEquals(1, partition.serial().size());
        assertEquals("edit", partition.serial().get(0).toolName());
    }

    @Test
    void unknownToolDefaultsToSerial() {
        var toolUses = List.of(toolUse("u1", "unknown_tool"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);
        assertTrue(partition.parallel().isEmpty());
        assertEquals(1, partition.serial().size());
    }

    // ================================
    //  Merge preserving order tests
    // ================================

    @Test
    void mergePreservingOrder_mixedBatch() {
        var toolUses = List.of(toolUse("u1", "read"), toolUse("u2", "bash"), toolUse("u3", "glob"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);

        var parallelResults =
                List.of(
                        new ToolResult("u1", "read-result", false, Map.of()),
                        new ToolResult("u3", "glob-result", false, Map.of()));
        var serialResults = List.of(new ToolResult("u2", "bash-result", false, Map.of()));

        var merged =
                ToolPartitioner.mergePreservingOrder(
                        toolUses, partition, parallelResults, serialResults);

        assertEquals(3, merged.size());
        // Order must match original toolUses
        assertEquals("read-result", merged.get(0).content());
        assertEquals("bash-result", merged.get(1).content());
        assertEquals("glob-result", merged.get(2).content());
    }

    @Test
    void mergePreservingOrder_allParallel() {
        var toolUses = List.of(toolUse("u1", "read"), toolUse("u2", "glob"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);

        var parallelResults =
                List.of(
                        new ToolResult("u1", "r1", false, Map.of()),
                        new ToolResult("u2", "r2", false, Map.of()));
        var serialResults = List.<ToolResult>of();

        var merged =
                ToolPartitioner.mergePreservingOrder(
                        toolUses, partition, parallelResults, serialResults);

        assertEquals(2, merged.size());
        assertEquals("r1", merged.get(0).content());
        assertEquals("r2", merged.get(1).content());
    }

    @Test
    void mergePreservingOrder_allSerial() {
        var toolUses = List.of(toolUse("u1", "bash"), toolUse("u2", "write"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);

        var parallelResults = List.<ToolResult>of();
        var serialResults =
                List.of(
                        new ToolResult("u1", "s1", false, Map.of()),
                        new ToolResult("u2", "s2", false, Map.of()));

        var merged =
                ToolPartitioner.mergePreservingOrder(
                        toolUses, partition, parallelResults, serialResults);

        assertEquals(2, merged.size());
        assertEquals("s1", merged.get(0).content());
        assertEquals("s2", merged.get(1).content());
    }

    @Test
    void mergePreservingOrder_empty() {
        var toolUses = List.<Content.ToolUseContent>of();
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);

        var merged =
                ToolPartitioner.mergePreservingOrder(toolUses, partition, List.of(), List.of());

        assertTrue(merged.isEmpty());
    }

    @Test
    void mergePreservingOrder_singleTool() {
        var toolUses = List.of(toolUse("u1", "read"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);

        var parallelResults = List.of(new ToolResult("u1", "single-result", false, Map.of()));
        var serialResults = List.<ToolResult>of();

        var merged =
                ToolPartitioner.mergePreservingOrder(
                        toolUses, partition, parallelResults, serialResults);

        assertEquals(1, merged.size());
        assertEquals("single-result", merged.get(0).content());
        assertEquals("u1", merged.get(0).toolUseId());
    }

    @Test
    void mergePreservingOrder_writesComeBeforeReadsInOriginalOrder() {
        // Model returns: write first, then read — order should be preserved
        var toolUses = List.of(toolUse("u1", "write"), toolUse("u2", "read"));
        var partition = ToolPartitioner.partition(toolUses, ToolPartitionerTest::resolver);

        var parallelResults = List.of(new ToolResult("u2", "read-r", false, Map.of()));
        var serialResults = List.of(new ToolResult("u1", "write-r", false, Map.of()));

        var merged =
                ToolPartitioner.mergePreservingOrder(
                        toolUses, partition, parallelResults, serialResults);

        assertEquals(2, merged.size());
        // Original order: write first, then read
        assertEquals("write-r", merged.get(0).content());
        assertEquals("read-r", merged.get(1).content());
    }
}
