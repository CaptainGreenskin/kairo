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

import io.kairo.api.message.Content;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Splits a batch of tool use requests into parallel-safe and serial groups.
 *
 * <p>A tool is parallel-safe when its {@link ToolSideEffect} is {@link ToolSideEffect#READ_ONLY}.
 * WRITE and SYSTEM_CHANGE tools must run serially to avoid race conditions on shared state.
 *
 * <p>All non-parallel-safe tools are executed serially after parallel tools complete. Results are
 * always returned in the original tool call order expected by the model.
 */
public final class ToolPartitioner {

    private ToolPartitioner() {}

    /**
     * A partitioned result: parallel-safe tools and serial-only tools, both in original order.
     *
     * @param parallel tools safe for concurrent execution
     * @param serial tools that must run sequentially
     */
    public record Partition(
            List<Content.ToolUseContent> parallel, List<Content.ToolUseContent> serial) {}

    /**
     * Partition tool calls by side-effect classification.
     *
     * @param toolUses the tool calls to partition
     * @param sideEffectResolver function that resolves a tool name to its side-effect
     *     classification
     * @return a Partition with parallel-safe and serial groups
     */
    public static Partition partition(
            List<Content.ToolUseContent> toolUses,
            Function<String, ToolSideEffect> sideEffectResolver) {
        List<Content.ToolUseContent> parallel = new ArrayList<>();
        List<Content.ToolUseContent> serial = new ArrayList<>();
        for (Content.ToolUseContent t : toolUses) {
            ToolSideEffect sideEffect = sideEffectResolver.apply(t.toolName());
            if (sideEffect == ToolSideEffect.READ_ONLY) {
                parallel.add(t);
            } else {
                serial.add(t);
            }
        }
        return new Partition(parallel, serial);
    }

    /**
     * Merge results from parallel and serial executions back into the original tool call order.
     *
     * <p>Each tool in the original list is matched to its result from the appropriate result pool
     * based on which partition it belonged to.
     *
     * @param toolUses the original tool call list (defines the ordering)
     * @param partition the partition that was used to split the tools
     * @param parallelResults results for parallel tools (in partition-relative order)
     * @param serialResults results for serial tools (in partition-relative order)
     * @return merged list in original tool call order
     */
    public static List<ToolResult> mergePreservingOrder(
            List<Content.ToolUseContent> toolUses,
            Partition partition,
            List<ToolResult> parallelResults,
            List<ToolResult> serialResults) {
        // Build ordered result lists keyed by tool position within each partition
        var parallelList = new ArrayList<>(parallelResults);
        var serialList = new ArrayList<>(serialResults);

        // Build sets for fast membership testing
        var parallelIds = new java.util.HashSet<String>();
        for (Content.ToolUseContent t : partition.parallel) {
            parallelIds.add(t.toolId());
        }

        List<ToolResult> merged = new ArrayList<>(toolUses.size());
        int pIdx = 0;
        int sIdx = 0;
        for (Content.ToolUseContent t : toolUses) {
            if (parallelIds.contains(t.toolId())) {
                merged.add(parallelList.get(pIdx++));
            } else {
                merged.add(serialList.get(sIdx++));
            }
        }
        return merged;
    }
}
